#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

#include "stable-diffusion.h"

namespace {
constexpr const char* LOG_TAG = "GameBrothDiffusion";

enum class NativeStage : int {
    IDLE = 0,
    LOADING_MODEL = 1,
    PREPARING = 2,
    DIFFUSION = 3,
    VAE_DECODE = 4,
    RGB_TRANSFER = 5,
    DONE = 6,
    FAILED = 7,
};

std::mutex g_mutex;
std::mutex g_log_mutex;
sd_ctx_t* g_ctx = nullptr;
std::string g_model_path;
std::string g_error;
std::string g_engine_error;
std::atomic<int> g_stage{static_cast<int>(NativeStage::IDLE)};
std::atomic<int> g_progress_step{0};
std::atomic<int> g_progress_total{0};
std::atomic<int> g_requested_steps{0};
std::atomic<int> g_output_width{0};
std::atomic<int> g_output_height{0};
std::atomic<bool> g_diffusion_started{false};
std::atomic<bool> g_diffusion_completed{false};

void log_info(const std::string& message) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", message.c_str());
}

void log_error(const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string compact_log_text(const char* text) {
    if (text == nullptr) return {};
    std::string result(text);
    result.erase(std::remove(result.begin(), result.end(), '\r'), result.end());
    while (!result.empty() && (result.back() == '\n' || result.back() == ' ')) result.pop_back();
    constexpr size_t MAX_DETAIL = 700;
    if (result.size() > MAX_DETAIL) result = result.substr(result.size() - MAX_DETAIL);
    return result;
}

void engine_log_callback(enum sd_log_level_t level, const char* text, void*) {
    const std::string message = compact_log_text(text);
    if (message.empty()) return;
    const int android_level = level == SD_LOG_ERROR ? ANDROID_LOG_ERROR
        : level == SD_LOG_WARN ? ANDROID_LOG_WARN
        : level == SD_LOG_INFO ? ANDROID_LOG_INFO
        : ANDROID_LOG_DEBUG;
    __android_log_print(android_level, LOG_TAG, "%s", message.c_str());
    if (level == SD_LOG_ERROR) {
        std::lock_guard<std::mutex> lock(g_log_mutex);
        g_engine_error = message;
    }
}

std::string last_engine_error() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    return g_engine_error;
}

void clear_engine_error() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_engine_error.clear();
}

void set_stage(NativeStage stage, int step = 0, int total = 0) {
    g_progress_step.store(std::max(0, step), std::memory_order_relaxed);
    g_progress_total.store(std::max(0, total), std::memory_order_relaxed);
    g_stage.store(static_cast<int>(stage), std::memory_order_release);
}

void set_error(const std::string& message) {
    g_error = message;
    set_stage(NativeStage::FAILED);
    log_error(message);
}

/**
 * stable-diffusion.cpp sends this shared callback for tensor loading, denoising and VAE tiling.
 * Denoising is intentionally tracked by diffusion_preview_callback instead, so a value such as
 * 374/374 can never be exposed as diffusion progress.
 */
void shared_progress_callback(int step, int steps, float, void*) {
    const NativeStage current = static_cast<NativeStage>(g_stage.load(std::memory_order_acquire));
    if (current == NativeStage::LOADING_MODEL || current == NativeStage::PREPARING) {
        // A completed tensor group is not a useful place to leave the UI parked: after the final
        // progress callback stable-diffusion.cpp can still spend time building runners/backends.
        // Show that unmeasured finalisation as PREPARING until either another tensor group begins
        // or new_sd_ctx() returns.
        if (steps > 0 && step >= steps) {
            set_stage(NativeStage::PREPARING);
        } else {
            set_stage(NativeStage::LOADING_MODEL, step, steps);
        }
        return;
    }
    if (current == NativeStage::DIFFUSION &&
        g_diffusion_completed.load(std::memory_order_acquire) && step == 0) {
        log_info("VAE decode start");
        set_stage(NativeStage::VAE_DECODE, step, steps);
        return;
    }
    if (current == NativeStage::VAE_DECODE) {
        set_stage(NativeStage::VAE_DECODE, step, steps);
    }
}

/** PREVIEW_PROJ is a denoiser-only callback in the pinned engine revision. */
void diffusion_preview_callback(int step, int, sd_image_t*, bool, void*) {
    const int total = std::max(1, g_requested_steps.load(std::memory_order_relaxed));
    const int logical_step = std::min(total, std::abs(step));
    if (!g_diffusion_started.exchange(true, std::memory_order_acq_rel)) {
        log_info("DIFFUSION start: total=" + std::to_string(total));
    }
    set_stage(NativeStage::DIFFUSION, logical_step, total);
    if (logical_step >= total && !g_diffusion_completed.exchange(true, std::memory_order_acq_rel)) {
        log_info("DIFFUSION end");
    }
}

void reset_generation_state() {
    g_requested_steps.store(0, std::memory_order_relaxed);
    g_output_width.store(0, std::memory_order_relaxed);
    g_output_height.store(0, std::memory_order_relaxed);
    g_diffusion_started.store(false, std::memory_order_relaxed);
    g_diffusion_completed.store(false, std::memory_order_relaxed);
    set_stage(NativeStage::IDLE);
}

void unload_locked() {
    if (g_ctx != nullptr) {
        log_info("MODEL unload start");
        free_sd_ctx(g_ctx);
        g_ctx = nullptr;
        log_info("MODEL unload end");
    }
    g_model_path.clear();
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
    g_error.clear();
    clear_engine_error();
    sd_set_log_callback(engine_log_callback, nullptr);
    sd_set_progress_callback(shared_progress_callback, nullptr);

    const std::string model_path = from_jstring(env, model_path_j);
    if (model_path.empty()) {
        set_error("empty model path");
        return env->NewStringUTF(g_error.c_str());
    }
    if (g_ctx != nullptr && g_model_path == model_path) {
        log_info("MODEL reuse loaded context");
        set_stage(NativeStage::PREPARING);
        return nullptr;
    }

    unload_locked();
    reset_generation_state();
    set_stage(NativeStage::LOADING_MODEL);
    log_info("MODEL load start");

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = model_path.c_str();
    params.n_threads = std::max(2, std::min(8, sd_get_num_physical_cores()));
    // Preserve the model's on-disk weight types. Forcing Q4_0 here makes a safetensors SDXL
    // checkpoint get quantised tensor-by-tensor on the phone every time a native context is
    // created. With mmap enabled that defeats the fast path and caused multi-minute stalls after
    // the last reported tensor group. Pre-quantised GGUF models remain quantised as stored.
    params.wtype = SD_TYPE_COUNT;
    params.rng_type = CPU_RNG;
    params.sampler_rng_type = CPU_RNG;
    params.enable_mmap = true;
    // Force the GPU class so an available Vulkan/Adreno backend is used instead of silently
    // falling back to CPU. "gpu" also accepts an integrated GPU, which is the Android case.
    params.backend = "gpu";
    params.auto_fit = true;
    // Vulkan flash-attention is not the fast path in this engine revision. Direct convolutions
    // are a better fit for the SDXL UNet/VAE on mobile Vulkan.
    params.flash_attn = false;
    params.diffusion_flash_attn = false;
    params.diffusion_conv_direct = true;
    params.vae_conv_direct = true;
    // Keep lazy loading so mmap-backed weights can be paged in as the engine needs them instead
    // of forcing the whole SDXL checkpoint resident before the first generation.
    params.eager_load = false;
    log_info("MODEL mode: backend=gpu; source weights; mmap=on; direct-conv=on; flash-attn=off");

    g_ctx = new_sd_ctx(&params);
    if (g_ctx == nullptr) {
        std::string detail = last_engine_error();
        set_error("new_sd_ctx failed" + (detail.empty() ? std::string() : ": " + detail));
        sd_set_progress_callback(nullptr, nullptr);
        return env->NewStringUTF(g_error.c_str());
    }
    if (!sd_ctx_supports_image_generation(g_ctx)) {
        unload_locked();
        set_error("selected model does not support image generation");
        sd_set_progress_callback(nullptr, nullptr);
        return env->NewStringUTF(g_error.c_str());
    }
    g_model_path = model_path;
    set_stage(NativeStage::PREPARING);
    log_info("MODEL load end");
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeUnloadModel(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    sd_set_preview_callback(nullptr, PREVIEW_NONE, 0, false, false, nullptr);
    sd_set_progress_callback(nullptr, nullptr);
    unload_locked();
    reset_generation_state();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeIsModelLoaded(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string message = g_error.empty() ? last_engine_error() : g_error;
    return env->NewStringUTF(message.empty() ? "unknown native error" : message.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeProgressStage(JNIEnv*, jclass) {
    return static_cast<jint>(g_stage.load(std::memory_order_acquire));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeProgressStep(JNIEnv*, jclass) {
    return static_cast<jint>(g_progress_step.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeProgressTotal(JNIEnv*, jclass) {
    return static_cast<jint>(g_progress_total.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeOutputWidth(JNIEnv*, jclass) {
    return static_cast<jint>(g_output_width.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeDiffusionBridge_nativeOutputHeight(JNIEnv*, jclass) {
    return static_cast<jint>(g_output_height.load(std::memory_order_relaxed));
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
    clear_engine_error();
    g_output_width.store(0, std::memory_order_relaxed);
    g_output_height.store(0, std::memory_order_relaxed);
    g_diffusion_started.store(false, std::memory_order_relaxed);
    g_diffusion_completed.store(false, std::memory_order_relaxed);
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

    g_requested_steps.store(gen.sample_params.sample_steps, std::memory_order_relaxed);
    set_stage(NativeStage::PREPARING);
    sd_set_progress_callback(shared_progress_callback, nullptr);
    // The generic callback is shared with tensor loading and VAE tiling. PREVIEW_PROJ is specific
    // to completed denoiser steps and its tiny latent projection is discarded immediately.
    sd_set_preview_callback(diffusion_preview_callback, PREVIEW_PROJ, 1, true, false, nullptr);

    sd_image_t* images = nullptr;
    int image_count = 0;
    const bool ok = generate_image(g_ctx, &gen, &images, &image_count);
    sd_set_preview_callback(nullptr, PREVIEW_NONE, 0, false, false, nullptr);
    sd_set_progress_callback(nullptr, nullptr);

    if (!ok || images == nullptr || image_count < 1 || images[0].data == nullptr) {
        if (images != nullptr && image_count > 0) free_sd_images(images, image_count);
        const std::string detail = last_engine_error();
        set_error("generate_image failed" + (detail.empty() ? std::string() : ": " + detail));
        return nullptr;
    }

    set_stage(NativeStage::RGB_TRANSFER);
    const sd_image_t& image = images[0];
    if (image.width == 0 || image.height == 0 || image.channel == 0) {
        free_sd_images(images, image_count);
        set_error("renderer returned an invalid image");
        return nullptr;
    }

    const size_t pixel_count = static_cast<size_t>(image.width) * static_cast<size_t>(image.height);
    if (pixel_count > static_cast<size_t>(std::numeric_limits<jsize>::max()) / 3u) {
        free_sd_images(images, image_count);
        set_error("renderer returned an image too large for the Java buffer");
        return nullptr;
    }
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
    g_output_width.store(static_cast<int>(image.width), std::memory_order_relaxed);
    g_output_height.store(static_cast<int>(image.height), std::memory_order_relaxed);
    free_sd_images(images, image_count);

    jbyteArray result = env->NewByteArray(static_cast<jsize>(rgb.size()));
    if (result == nullptr) {
        set_error("failed to allocate Java image buffer");
        return nullptr;
    }
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(rgb.size()),
        reinterpret_cast<const jbyte*>(rgb.data()));
    if (env->ExceptionCheck()) {
        set_error("failed to copy RGB bytes into the Java buffer");
        return nullptr;
    }
    set_stage(NativeStage::DONE);
    log_info("RGB transfer end: bytes=" + std::to_string(rgb.size()));
    return result;
}
