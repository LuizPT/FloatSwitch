package me.diluir.floatswitch

import java.util.Locale

enum class AppLanguage(
    val languageTag: String,
    val iconResource: Int,
    val displayNameResource: Int,
) {
    AUTOMATIC("", R.drawable.flag_united_kingdom, R.string.language_automatic),
    ENGLISH("en", R.drawable.flag_united_kingdom, R.string.language_english),
    PORTUGUESE_PORTUGAL(
        "pt-PT",
        R.drawable.flag_portugal,
        R.string.language_portuguese_portugal,
    ),
    PORTUGUESE_BRAZIL(
        "pt-BR",
        R.drawable.flag_brazil,
        R.string.language_portuguese_brazil,
    ),
    SPANISH("es", R.drawable.flag_spain, R.string.language_spanish),
    FRENCH("fr", R.drawable.flag_france, R.string.language_french),
    GERMAN("de", R.drawable.flag_germany, R.string.language_german),
    ITALIAN("it", R.drawable.flag_italy, R.string.language_italian),
    DUTCH("nl", R.drawable.flag_netherlands, R.string.language_dutch),
    POLISH("pl", R.drawable.flag_poland, R.string.language_polish),
    UKRAINIAN("uk", R.drawable.flag_ukraine, R.string.language_ukrainian),
    CHINESE_SIMPLIFIED(
        "zh-CN",
        R.drawable.flag_china,
        R.string.language_chinese_simplified,
    ),
    CHINESE_TRADITIONAL(
        "zh-TW",
        R.drawable.flag_taiwan,
        R.string.language_chinese_traditional,
    ),
    JAPANESE("ja", R.drawable.flag_japan, R.string.language_japanese),
    KOREAN("ko", R.drawable.flag_south_korea, R.string.language_korean),
    ARABIC("ar", R.drawable.flag_saudi_arabia, R.string.language_arabic),
    TURKISH("tr", R.drawable.flag_turkey, R.string.language_turkish),
    HINDI("hi", R.drawable.flag_india, R.string.language_hindi),
}

object AppLanguageRules {
    val supportedLanguages: List<AppLanguage> = AppLanguage.entries

    fun isRussianLocaleTag(languageTag: String): Boolean =
        Locale.forLanguageTag(languageTag).language.equals("ru", ignoreCase = true)

    fun fromApplicationLanguageTags(languageTags: String?): AppLanguage {
        val firstTag = languageTags.orEmpty().split(',').firstOrNull().orEmpty().trim()
        if (firstTag.isEmpty()) return AppLanguage.AUTOMATIC
        return matchSupportedLocale(Locale.forLanguageTag(firstTag), russianAutomaticFallback = false)
            ?: AppLanguage.ENGLISH
    }

    fun effectiveAutomaticLanguage(systemLanguageTags: List<String>): AppLanguage =
        systemLanguageTags.asSequence()
            .map(Locale::forLanguageTag)
            .mapNotNull { matchSupportedLocale(it, russianAutomaticFallback = true) }
            .firstOrNull()
            ?: AppLanguage.ENGLISH

    private fun matchSupportedLocale(
        locale: Locale,
        russianAutomaticFallback: Boolean,
    ): AppLanguage? = when (locale.language) {
        "en" -> AppLanguage.ENGLISH
        "pt" -> if (locale.country.equals("BR", ignoreCase = true)) {
            AppLanguage.PORTUGUESE_BRAZIL
        } else {
            AppLanguage.PORTUGUESE_PORTUGAL
        }

        "es" -> AppLanguage.SPANISH
        "fr" -> AppLanguage.FRENCH
        "de" -> AppLanguage.GERMAN
        "it" -> AppLanguage.ITALIAN
        "nl" -> AppLanguage.DUTCH
        "pl" -> AppLanguage.POLISH
        "uk" -> AppLanguage.UKRAINIAN
        // Russian is not selectable; only automatic Russian systems use Ukrainian.
        "ru" -> if (russianAutomaticFallback) AppLanguage.UKRAINIAN else null
        "zh" -> if (
            locale.country.equals("TW", ignoreCase = true) ||
            locale.country.equals("HK", ignoreCase = true) ||
            locale.script.equals("Hant", ignoreCase = true)
        ) {
            AppLanguage.CHINESE_TRADITIONAL
        } else {
            AppLanguage.CHINESE_SIMPLIFIED
        }

        "ja" -> AppLanguage.JAPANESE
        "ko" -> AppLanguage.KOREAN
        "ar" -> AppLanguage.ARABIC
        "tr" -> AppLanguage.TURKISH
        "hi" -> AppLanguage.HINDI
        else -> null
    }
}
