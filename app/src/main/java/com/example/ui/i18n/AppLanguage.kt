package com.example.ui.i18n

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिंदी");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

val AppLanguage.strings: AppStrings
    get() = when (this) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.HINDI -> HindiStrings
    }

