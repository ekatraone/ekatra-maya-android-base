/**
 * llama_jni.cpp - JNI bridge for llama.cpp
 * 
 * Real implementation using modern llama.cpp library API.
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <exception>
#include <cstdlib>

#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Redirect llama.cpp internal logging to Android logcat
static void llama_android_log_callback(enum ggml_log_level level, const char * text, void * /* user_data */) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            __android_log_print(ANDROID_LOG_ERROR, "llama.cpp", "%s", text);
            break;
        case GGML_LOG_LEVEL_WARN:
            __android_log_print(ANDROID_LOG_WARN, "llama.cpp", "%s", text);
            break;
        case GGML_LOG_LEVEL_INFO:
            __android_log_print(ANDROID_LOG_INFO, "llama.cpp", "%s", text);
            break;
        default:
            __android_log_print(ANDROID_LOG_DEBUG, "llama.cpp", "%s", text);
            break;
    }
}

// Global State
static bool g_backend_initialized = false;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_sampler = nullptr;
static std::mutex g_mutex;

static int g_n_threads = 4;
static int g_n_ctx = 2048;
static int g_n_past = 0;
static bool g_is_generating = false;

// Helper: Convert token to string using modern API
static std::string token_to_piece(const llama_model* model, llama_token token) {
    std::vector<char> buf(256);
    const llama_vocab* vocab = llama_model_get_vocab(model);
    int n = llama_token_to_piece(vocab, token, buf.data(), buf.size(), 0, true);
    if (n < 0) {
        buf.resize(-n);
        n = llama_token_to_piece(vocab, token, buf.data(), buf.size(), 0, true);
    }
    return std::string(buf.data(), n);
}

// Helper: Tokenize text using modern API
static std::vector<llama_token> tokenize(const llama_model* model, const std::string& text, bool add_bos) {
    int n_tokens_estimate = text.length() + (add_bos ? 1 : 0) + 16;
    std::vector<llama_token> tokens(n_tokens_estimate);
    const llama_vocab* vocab = llama_model_get_vocab(model);
    int n_tokens = llama_tokenize(vocab, text.c_str(), text.length(), tokens.data(), tokens.size(), add_bos, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, text.c_str(), text.length(), tokens.data(), tokens.size(), add_bos, true);
    }
    tokens.resize(n_tokens);
    return tokens;
}

extern "C" {

// Backend lifecycle (called from Application)
JNIEXPORT void JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativeInitBackend(
    JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_backend_initialized) {
        llama_log_set(llama_android_log_callback, nullptr);
        llama_backend_init();
        g_backend_initialized = true;
        LOGI("Backend initialized");
    }
}

JNIEXPORT void JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativeFreeBackend(
    JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
        LOGI("Backend freed");
    }
}

// Error codes for loadModel
// 0 = success, 1 = file_error, 2 = gpu_error, 3 = oom_or_ctx, 4 = backend_uninitialized

JNIEXPORT jint JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint threads,
    jint context_size,
    jboolean use_gpu,
    jint gpu_layers) {
    
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_backend_initialized) {
        LOGE("Backend not initialized; call nativeInitBackend first");
        return 4;
    }
    
    // Use provided parameters
    g_n_threads = threads > 0 ? threads : 4;
    g_n_ctx = context_size > 0 ? context_size : 2048;
    
    // Clean up any existing model
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    
    const char* pathStr = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", pathStr);
    
    // CPU-only inference (Vulkan stripped from build)
    (void)use_gpu;
    (void)gpu_layers;
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    
    try {
        g_model = llama_model_load_from_file(pathStr, model_params);
    } catch (const std::exception & e) {
        LOGE("Exception while loading model: %s", e.what());
        g_model = nullptr;
    }
    env->ReleaseStringUTFChars(model_path, pathStr);
    
    if (!g_model) {
        LOGE("Failed to load model");
        return use_gpu ? 2 : 1;
    }
    
    // Create context with modern API
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_n_ctx;
    ctx_params.n_threads = g_n_threads;
    ctx_params.n_threads_batch = g_n_threads;
    
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return 3;
    }
    
    // Create sampler chain
    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));
    
    LOGI("Model loaded! Vocab: %d, Ctx: %d", 
         llama_vocab_n_tokens(llama_model_get_vocab(g_model)), llama_n_ctx(g_ctx));
    
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativePrepare(
        JNIEnv* env, jobject /* this */, jstring prompt_text) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_model || !g_ctx) {
        LOGE("Model not loaded");
        return JNI_FALSE;
    }
    
    const char* promptStr = env->GetStringUTFChars(prompt_text, nullptr);
    std::string prompt(promptStr);
    env->ReleaseStringUTFChars(prompt_text, promptStr);
    
    // Tokenize prompt
    auto tokens = tokenize(g_model, prompt, true);
    LOGI("Prompt tokenized: %zu tokens (context at %d tokens)", tokens.size(), g_n_past);
    
    // Evaluate prompt in batches
    int n_batch = 512;
    for (int i = 0; i < (int)tokens.size(); i += n_batch) {
        int n_eval = (int)tokens.size() - i;
        if (n_eval > n_batch) n_eval = n_batch;
        
        llama_batch batch = llama_batch_get_one(&tokens[i], n_eval);
        
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to evaluate prompt");
            return JNI_FALSE;
        }
        
        g_n_past += n_eval;
    }
    
    LOGI("Prompt evaluated, ready to generate");
    g_is_generating = true;
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativeGenerateToken(
        JNIEnv* env, jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_model || !g_ctx || !g_sampler || !g_is_generating) {
        LOGE("Not ready to generate");
        return env->NewStringUTF("");
    }
    
    // Sample next token
    llama_token new_token = llama_sampler_sample(g_sampler, g_ctx, -1);
    
    // Check for end of generation using modern API
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token)) {
        LOGI("End of generation");
        g_is_generating = false;
        return env->NewStringUTF("");
    }
    
    // Decode token to string
    std::string piece = token_to_piece(g_model, new_token);
    
    // Update context with new token
    llama_batch batch = llama_batch_get_one(&new_token, 1);
    
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to evaluate");
        return env->NewStringUTF("");
    }
    
    g_n_past++;
    
    return env->NewStringUTF(piece.c_str());
}

JNIEXPORT void JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativeClearContext(
        JNIEnv* /* env */, jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (g_ctx) {
        LOGI("Clearing conversation context");
        llama_memory_t mem = llama_get_memory(g_ctx);
        llama_memory_clear(mem, true);  // true = also clear data buffers
        g_n_past = 0;
        g_is_generating = false;
    }
}

JNIEXPORT void JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativeUnload(
        JNIEnv* /* env */, jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    LOGI("Unloading model");
    
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    
    g_n_past = 0;
    g_is_generating = false;
    
    LOGI("Model unloaded");
}

} // extern "C"
