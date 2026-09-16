#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "stable-diffusion.h"

namespace {
std::mutex g_mutex;
sd_ctx_t* g_ctx = nullptr;
std::string g_error;
std::atomic<int> g_progress_step{0};
std::atomic<int> g_progress_total{0};

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void set_error(const std::string& message) {
    g_error = message;
}

void reset_progress() {
    g_progress_step.store(0, std::memory_order_relaxed);
    g_progress_total.store(0, std::memory_order_relaxed);
}

void progress_callback(int step, int steps, float, void*) {
    g_progress_total.store(std::max(0, steps), std::memory_order_relaxed);
    g_progress_step.store(std::max(0, step), std::memory_order_relaxed);
}

void unload_locked() {
    if (g_ctx != nullptr) {
        free_sd_ctx(g_ctx);
        g_ctx = nullptr;
    }
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeRuntimeInfo(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::string info = std::string("stable-diffusion.cpp ") + sd_version() + " / " + sd_get_system_info();
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeLoadModel(
        JNIEnv* env, jclass, jstring model_path_j) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
    reset_progress();
    g_error.clear();

    const std::string model_path = from_jstring(env, model_path_j);
    if (model_path.empty()) {
        set_error("empty model path");
        return env->NewStringUTF(g_error.c_str());
    }

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = model_path.c_str();
    params.n_threads = std::max(2, std::min(8, sd_get_num_physical_cores()));
    // First Android profile prioritises reliability and RAM headroom. WAI/SDXL is quantised
    // on load to Q4_0; on a 16 GB phone this leaves room for Android, the game and PNG buffers.
    params.wtype = SD_TYPE_Q4_0;
    params.rng_type = CPU_RNG;
    params.sampler_rng_type = CPU_RNG;
    params.enable_mmap = true;
    params.flash_attn = true;
    params.diffusion_flash_attn = true;
    params.auto_fit = true;
    params.eager_load = false;

    g_ctx = new_sd_ctx(&params);
    if (g_ctx == nullptr) {
        set_error("new_sd_ctx failed; model may be unsupported or Android killed the allocation");
        return env->NewStringUTF(g_error.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeUnloadModel(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_error.empty() ? "unknown native error" : g_error.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeProgressStep(JNIEnv*, jclass) {
    return static_cast<jint>(g_progress_step.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeProgressTotal(JNIEnv*, jclass) {
    return static_cast<jint>(g_progress_total.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeGenerateRgb(
        JNIEnv* env,
        jclass,
        jstring prompt_j,
        jstring negative_prompt_j,
        jint width,
        jint height,
        jint steps,
        jfloat cfg,
        jlong seed) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_error.clear();
    reset_progress();
    if (g_ctx == nullptr) {
        set_error("model context is not loaded");
        return nullptr;
    }

    const std::string prompt = from_jstring(env, prompt_j);
    const std::string negative_prompt = from_jstring(env, negative_prompt_j);
    if (prompt.empty()) {
        set_error("empty prompt");
        return nullptr;
    }

    sd_img_gen_params_t gen;
    sd_img_gen_params_init(&gen);
    gen.prompt = prompt.c_str();
    gen.negative_prompt = negative_prompt.c_str();
    gen.clip_skip = 2;
    gen.width = std::max(256, static_cast<int>(width));
    gen.height = std::max(256, static_cast<int>(height));
    gen.sample_params.sample_steps = std::max(1, static_cast<int>(steps));
    gen.sample_params.guidance.txt_cfg = std::max(1.0f, static_cast<float>(cfg));
    gen.sample_params.sample_method = EULER_A_SAMPLE_METHOD;
    gen.seed = static_cast<int64_t>(seed);
    gen.batch_count = 1;
    gen.vae_tiling_params.enabled = true;

    g_progress_total.store(gen.sample_params.sample_steps, std::memory_order_relaxed);
    sd_set_progress_callback(progress_callback, nullptr);

    sd_image_t* images = nullptr;
    int image_count = 0;
    const bool ok = generate_image(g_ctx, &gen, &images, &image_count);
    sd_set_progress_callback(nullptr, nullptr);

    if (!ok || images == nullptr || image_count < 1 || images[0].data == nullptr) {
        if (images != nullptr && image_count > 0) free_sd_images(images, image_count);
        set_error("generate_image failed");
        return nullptr;
    }

    g_progress_step.store(gen.sample_params.sample_steps, std::memory_order_relaxed);
    const sd_image_t& image = images[0];
    if (image.width == 0 || image.height == 0 || image.channel == 0) {
        free_sd_images(images, image_count);
        set_error("renderer returned an invalid image");
        return nullptr;
    }

    const size_t pixel_count = static_cast<size_t>(image.width) * static_cast<size_t>(image.height);
    std::vector<uint8_t> rgb(pixel_count * 3u);
    for (size_t i = 0; i < pixel_count; ++i) {
        const size_t src = i * image.channel;
        const size_t dst = i * 3u;
        if (image.channel >= 3) {
            rgb[dst] = image.data[src];
            rgb[dst + 1] = image.data[src + 1];
            rgb[dst + 2] = image.data[src + 2];
        } else {
            rgb[dst] = rgb[dst + 1] = rgb[dst + 2] = image.data[src];
        }
    }
    free_sd_images(images, image_count);

    jbyteArray result = env->NewByteArray(static_cast<jsize>(rgb.size()));
    if (result == nullptr) {
        set_error("failed to allocate Java image buffer");
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(rgb.size()), reinterpret_cast<const jbyte*>(rgb.data()));
    return result;
}
