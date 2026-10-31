package com.handymobile.app.text

import com.handymobile.app.settings.DictationMode

/**
 * User dictionary, one entry per line:
 *  - `word`            vocabulary hint, fed to Whisper as part of its initial prompt;
 *  - `wrong => right`  exact whole-word replacement applied after transcription (optional);
 *  - `# comment`       ignored.
 * Nothing is replaced unless the user wrote the rule.
 */
object Dictionary {
    /** Darija (Algerian) sample sentence mixing Arabic script and French words. Steers style only. */
    private const val DARIJA_PROMPT =
        "واش راك؟ لاباس الحمد لله. راني نخدم في le bureau، وبعد le travail نروح للدار. " +
            "بصح ما عنديش الوقت، نهار الجمعة نشوفك إن شاء الله."
    private const val MAX_PROMPT_CHARS = 500

    class Parsed(val hints: List<String>, val replacements: List<Pair<Regex, String>>)

    fun parse(text: String): Parsed {
        val hints = mutableListOf<String>()
        val rules = mutableListOf<Pair<Regex, String>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val idx = line.indexOf("=>")
            if (idx < 0) {
                hints += line
                continue
            }
            val wrong = line.substring(0, idx).trim()
            val right = line.substring(idx + 2).trim()
            if (wrong.isEmpty()) continue
            val regex = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(wrong) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE,
            )
            rules += regex to Regex.escapeReplacement(right)
            if (right.isNotEmpty()) hints += right
        }
        return Parsed(hints.distinct(), rules)
    }

    fun buildPrompt(mode: DictationMode, parsed: Parsed): String? {
        val parts = mutableListOf<String>()
        if (mode == DictationMode.DARIJA) parts += DARIJA_PROMPT
        if (parsed.hints.isNotEmpty()) parts += parsed.hints.joinToString("، ")
        return parts.joinToString(" ").takeIf { it.isNotBlank() }?.takeLast(MAX_PROMPT_CHARS)
    }

    fun apply(text: String, parsed: Parsed): String =
        parsed.replacements.fold(text) { acc, (regex, repl) -> regex.replace(acc, repl) }
}
