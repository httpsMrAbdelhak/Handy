package com.handymobile.app.settings

import android.content.Context
import androidx.annotation.StringRes
import com.handymobile.app.R
import com.handymobile.app.model.WhisperModel

/** [code] is what whisper.cpp receives; DARIJA is Arabic plus a dialect/French-mix prompt. */
enum class DictationMode(val code: String, @StringRes val label: Int) {
    DARIJA("ar", R.string.mode_darija),
    AUTO("auto", R.string.mode_auto),
    FR("fr", R.string.mode_fr),
    AR("ar", R.string.mode_ar),
    EN("en", R.string.mode_en),
}

data class Settings(
    val model: WhisperModel = WhisperModel.TINY,
    val mode: DictationMode = DictationMode.DARIJA,
    val fast: Boolean = false,
    val dictionary: String = "",
    val corrections: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = Settings(
        model = WhisperModel.entries.firstOrNull { it.name == sp.getString("model", null) } ?: WhisperModel.TINY,
        mode = DictationMode.entries.firstOrNull { it.name == sp.getString("mode", null) } ?: DictationMode.DARIJA,
        fast = sp.getBoolean("fast", false),
        dictionary = sp.getString("dictionary", "").orEmpty(),
        corrections = sp.getBoolean("corrections", true),
    )

    fun save(s: Settings) = sp.edit()
        .putString("model", s.model.name)
        .putString("mode", s.mode.name)
        .putBoolean("fast", s.fast)
        .putString("dictionary", s.dictionary)
        .putBoolean("corrections", s.corrections)
        .apply()
}
