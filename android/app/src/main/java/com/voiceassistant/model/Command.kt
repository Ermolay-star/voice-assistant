package com.voiceassistant.model

sealed class Command {
    /** Позвонить: имя контакта или номер */
    data class Call(val contact: String) : Command()

    /** Задать вопрос GigaChat */
    data class Ask(val question: String) : Command()
}
