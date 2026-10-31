#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <exception>
#include <mutex>
#include <string>

#include "ggml-backend.h"
#include "whisper.h"

#define TAG "HandyWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
std::once_flag g_backends_loaded;

std::string to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeInit(
        JNIEnv *env, jobject, jstring jpath, jstring jlibdir) {
    const std::string path = to_std(env, jpath);
    const std::string libdir = to_std(env, jlibdir);
    jlong result = 0;
    try {
        // Loads the best libggml-cpu-* variant for this device from the app's lib dir.
        std::call_once(g_backends_loaded, [&] { ggml_backend_load_all_from_path(libdir.c_str()); });
        whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;
        cparams.flash_attn = false;
        result = reinterpret_cast<jlong>(whisper_init_from_file_with_params(path.c_str(), cparams));
    } catch (const std::exception &e) {
        LOGE("init failed: %s", e.what());
    } catch (...) {
        LOGE("init failed: unknown exception");
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeFree(JNIEnv *, jobject, jlong ptr) {
    if (ptr != 0) {
        whisper_free(reinterpret_cast<whisper_context *>(ptr));
    }
}

JNIEXPORT jstring JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeSystemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

// Returns the UTF-8 transcript as bytes, or null on failure.
// language: "auto" or an ISO code. prompt: optional initial prompt (may be empty).
// fast: smaller encoder context + no temperature fallback.
JNIEXPORT jbyteArray JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeTranscribe(
        JNIEnv *env, jobject, jlong ptr, jint threads, jstring jlang, jstring jprompt,
        jboolean fast, jfloatArray jsamples) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return nullptr;

    const std::string language = to_std(env, jlang);
    const std::string prompt = to_std(env, jprompt);

    const jsize n = env->GetArrayLength(jsamples);
    jfloat *samples = env->GetFloatArrayElements(jsamples, nullptr);
    if (samples == nullptr) return nullptr;

    std::string text;
    bool ok = false;
    try {
        const float seconds = static_cast<float>(n) / WHISPER_SAMPLE_RATE;
        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = threads;
        params.language = language.c_str();  // "auto" = detect, then transcribe
        params.detect_language = false;
        params.translate = false;
        params.no_context = true;
        params.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.greedy.best_of = 1;
        if (seconds <= 28.0f) {  // one window: timestamps are not needed
            params.no_timestamps = true;
            params.single_segment = true;
        }
        if (fast) {
            // The encoder always processes 30 s unless told otherwise.
            params.audio_ctx = std::min(1500, std::max(384, static_cast<int>(seconds * 50.0f) + 100));
            params.temperature_inc = 0.0f;
        }

        if (whisper_full(ctx, params, samples, n) == 0) {
            const int segments = whisper_full_n_segments(ctx);
            for (int i = 0; i < segments; ++i) {
                const char *seg = whisper_full_get_segment_text(ctx, i);
                if (seg != nullptr) text += seg;
            }
            ok = true;
        } else {
            LOGE("whisper_full failed");
        }
    } catch (const std::exception &e) {
        LOGE("transcribe failed: %s", e.what());
    } catch (...) {
        LOGE("transcribe failed: unknown exception");
    }
    env->ReleaseFloatArrayElements(jsamples, samples, JNI_ABORT);
    if (!ok) return nullptr;

    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    return out;
}

}  // extern "C"
