package com.voiceassistant

import com.voiceassistant.model.Command

/**
 * Разбирает голосовую команду:
 *   "позвони / набери [кому]"  → Command.Call
 *   всё остальное              → Command.Ask
 */
object CommandParser {

    private val CALL_RE = Regex(
        """(?:позвони|набери|позвоните|наберите)(?:\s+на\s+номер)?\s+(.+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): Command {
        val text = input.trim()
        CALL_RE.find(text)?.let { return Command.Call(it.groupValues[1].trim()) }
        return Command.Ask(text)
    }
}
