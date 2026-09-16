#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {
constexpr const char * TAG = "GameBrothLlama";

std::mutex g_mutex;
std::once_flag g_backend_once;
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
const llama_vocab * g_vocab = nullptr;
int g_batch_size = 256;
std::string g_error;

std::string from_jstring(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void set_error(const std::string & message) {
    g_error = message;
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", message.c_str());
}

void ensure_backend() {
    std::call_once(g_backend_once, [] {
        llama_log_set([](enum ggml_log_level level, const char * text, void *) {
            if (level >= GGML_LOG_LEVEL_WARN) {
                __android_log_print(level >= GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, "%s", text);
            }
        }, nullptr);
        llama_backend_init();
    });
}

void unload_locked() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

bool decode_prompt_tokens(const std::vector<llama_token> & tokens) {
    size_t offset = 0;
    while (offset < tokens.size()) {
        const int count = static_cast<int>(std::min<size_t>(static_cast<size_t>(g_batch_size), tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(tokens.data() + offset), count);
        const int rc = llama_decode(g_ctx, batch);
        if (rc != 0) {
            set_error("llama_decode failed while processing prompt, rc=" + std::to_string(rc));
            return false;
        }
        offset += static_cast<size_t>(count);
    }
    return true;
}

std::string token_piece(llama_token token) {
    char small[512];
    int n = llama_token_to_piece(g_vocab, token, small, sizeof(small), 0, true);
    if (n >= 0) return std::string(small, static_cast<size_t>(n));

    const int needed = -n;
    std::vector<char> large(static_cast<size_t>(needed));
    n = llama_token_to_piece(g_vocab, token, large.data(), large.size(), 0, true);
    if (n < 0) return {};
    return std::string(large.data(), static_cast<size_t>(n));
}

std::string apply_chat_template(const std::string & system_prompt, const std::string & user_prompt) {
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    if (tmpl == nullptr) {
        return "System:\n" + system_prompt + "\n\nUser:\n" + user_prompt + "\n\nAssistant:\n";
    }

    llama_chat_message messages[2] = {
        {"system", system_prompt.c_str()},
        {"user", user_prompt.c_str()},
    };

    int required = llama_chat_apply_template(tmpl, messages, 2, true, nullptr, 0);
    if (required < 0) {
        set_error("llama_chat_apply_template failed while sizing prompt");
        return {};
    }
    std::vector<char> formatted(static_cast<size_t>(required) + 1u, '\0');
    int written = llama_chat_apply_template(tmpl, messages, 2, true, formatted.data(), formatted.size());
    if (written < 0) {
        set_error("llama_chat_apply_template failed while formatting prompt");
        return {};
    }
    return std::string(formatted.data(), static_cast<size_t>(written));
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeTextBridge_nativeRuntimeInfo(JNIEnv * env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ensure_backend();
    std::string info = std::string("llama.cpp ") + llama_version() + " / " + llama_print_system_info();
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeTextBridge_nativeLoadModel(
        JNIEnv * env,
        jclass,
        jstring model_path_j,
        jint context_tokens,
        jint threads,
        jint batch_size) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ensure_backend();
    unload_locked();
    g_error.clear();

    const std::string model_path = from_jstring(env, model_path_j);
    if (model_path.empty()) {
        set_error("empty model path");
        return env->NewStringUTF(g_error.c_str());
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;

    g_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (g_model == nullptr) {
        set_error("llama_model_load_from_file failed; check GGUF compatibility and free RAM");
        return env->NewStringUTF(g_error.c_str());
    }
    g_vocab = llama_model_get_vocab(g_model);
    if (g_vocab == nullptr) {
        set_error("loaded model has no vocabulary");
        unload_locked();
        return env->NewStringUTF(g_error.c_str());
    }

    const int safe_context = std::clamp(static_cast<int>(context_tokens), 2048, 16384);
    const int safe_threads = std::clamp(static_cast<int>(threads), 2, 12);
    g_batch_size = std::clamp(static_cast<int>(batch_size), 64, 1024);
    g_batch_size = std::min(g_batch_size, safe_context);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(safe_context);
    ctx_params.n_batch = static_cast<uint32_t>(g_batch_size);
    ctx_params.n_ubatch = static_cast<uint32_t>(g_batch_size);
    ctx_params.n_threads = safe_threads;
    ctx_params.n_threads_batch = safe_threads;
    ctx_params.offload_kqv = false;
    ctx_params.op_offload = false;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        set_error("llama_init_from_model failed while allocating the context/KV cache");
        unload_locked();
        return env->NewStringUTF(g_error.c_str());
    }

    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeTextBridge_nativeGenerate(
        JNIEnv * env,
        jclass,
        jstring system_prompt_j,
        jstring user_prompt_j,
        jint max_tokens,
        jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_error.clear();

    if (g_model == nullptr || g_ctx == nullptr || g_vocab == nullptr) {
        set_error("text model is not loaded");
        return nullptr;
    }

    const std::string system_prompt = from_jstring(env, system_prompt_j);
    const std::string user_prompt = from_jstring(env, user_prompt_j);
    if (user_prompt.empty()) {
        set_error("empty user prompt");
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(g_ctx), false);

    const std::string prompt = apply_chat_template(system_prompt, user_prompt);
    if (prompt.empty()) return nullptr;

    const int token_count = -llama_tokenize(g_vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (token_count <= 0) {
        set_error("failed to size prompt tokenization");
        return nullptr;
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(token_count));
    const int tokenized = llama_tokenize(
        g_vocab,
        prompt.c_str(),
        prompt.size(),
        prompt_tokens.data(),
        static_cast<int32_t>(prompt_tokens.size()),
        true,
        true);
    if (tokenized < 0) {
        set_error("failed to tokenize prompt");
        return nullptr;
    }
    prompt_tokens.resize(static_cast<size_t>(tokenized));

    const int context_size = static_cast<int>(llama_n_ctx(g_ctx));
    if (static_cast<int>(prompt_tokens.size()) >= context_size - 16) {
        set_error("prompt does not fit in the configured context window");
        return nullptr;
    }

    const int requested = std::clamp(static_cast<int>(max_tokens), 1, 4096);
    const int generation_limit = std::min(requested, context_size - static_cast<int>(prompt_tokens.size()) - 8);
    if (generation_limit <= 0) {
        set_error("no context space remains for generation");
        return nullptr;
    }

    if (!decode_prompt_tokens(prompt_tokens)) return nullptr;

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) {
        set_error("failed to create sampler");
        return nullptr;
    }

    const float safe_temp = std::clamp(static_cast<float>(temperature), 0.05f, 2.0f);
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(safe_temp));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string response;
    for (int produced = 0; produced < generation_limit; ++produced) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, token)) break;

        const std::string piece = token_piece(token);
        if (piece.empty()) {
            llama_sampler_free(sampler);
            set_error("failed to decode generated token");
            return nullptr;
        }
        response += piece;

        llama_batch next = llama_batch_get_one(&token, 1);
        const int rc = llama_decode(g_ctx, next);
        if (rc != 0) {
            llama_sampler_free(sampler);
            set_error("llama_decode failed during generation, rc=" + std::to_string(rc));
            return nullptr;
        }
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeTextBridge_nativeLastError(JNIEnv * env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_error.empty() ? "unknown llama.cpp error" : g_error.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_sendmefile77_gamebroth_ai_NativeTextBridge_nativeUnloadModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
}
