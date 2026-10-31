#include <jni.h>
#include <android/log.h>

#include <exception>
#include <string>

#include "whisper.h"

#define TAG "HandyWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeInit(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    jlong result = 0;
    try {
        whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;
        cparams.flash_attn = false;
        result = reinterpret_cast<jlong>(whisper_init_from_file_with_params(path, cparams));
    } catch (const std::exception &e) {
        LOGE("init failed: %s", e.what());
    } catch (...) {
        LOGE("init failed: unknown exception");
    }
    env->ReleaseStringUTFChars(jpath, path);
    return result;
}

JNIEXPORT void JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeFree(JNIEnv *, jobject, jlong ptr) {
    if (ptr != 0) {
        whisper_free(reinterpret_cast<whisper_context *>(ptr));
    }
}

// Returns the UTF-8 transcript as bytes, or null on failure.
JNIEXPORT jbyteArray JNICALL
Java_com_handymobile_app_whisper_WhisperLib_nativeTranscribe(
        JNIEnv *env, jobject, jlong ptr, jint threads, jstring jlang, jfloatArray jsamples) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return nullptr;

    const char *lang = env->GetStringUTFChars(jlang, nullptr);
    std::string language(lang);
    env->ReleaseStringUTFChars(jlang, lang);

    const jsize n = env->GetArrayLength(jsamples);
    jfloat *samples = env->GetFloatArrayElements(jsamples, nullptr);
    if (samples == nullptr) return nullptr;

    std::string text;
    bool ok = false;
    try {
        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = threads;
        params.language = language.c_str();
        params.detect_language = false;
        params.translate = false;
        params.no_context = true;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.greedy.best_of = 1;

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
