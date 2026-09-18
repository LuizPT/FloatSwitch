package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageRulesTest {
    @Test fun `empty application locale means automatic`() {
        assertEquals(AppLanguage.AUTOMATIC, AppLanguageRules.fromApplicationLanguageTags(""))
    }

    @Test fun `manual locale maps to the selected language`() {
        assertEquals(AppLanguage.PORTUGUESE_PORTUGAL, AppLanguageRules.fromApplicationLanguageTags("pt-PT"))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguageRules.fromApplicationLanguageTags("zh-Hant-TW"))
    }

    @Test fun `returning to automatic clears the application locale`() {
        assertEquals(AppLanguage.AUTOMATIC, AppLanguageRules.fromApplicationLanguageTags(null))
    }

    @Test fun `unsupported manual locale falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguageRules.fromApplicationLanguageTags("sv-SE"))
    }

    @Test fun `automatic uses the first supported system locale`() {
        assertEquals(AppLanguage.SPANISH, AppLanguageRules.effectiveAutomaticLanguage(listOf("sv-SE", "es-ES", "en-GB")))
    }

    @Test fun `unsupported system locales fall back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguageRules.effectiveAutomaticLanguage(listOf("sv-SE", "fi-FI")))
    }

    @Test fun `Russian system locale resolves to Ukrainian`() {
        assertEquals(AppLanguage.UKRAINIAN, AppLanguageRules.effectiveAutomaticLanguage(listOf("ru")))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguageRules.effectiveAutomaticLanguage(listOf("ru-RU")))
        assert(AppLanguageRules.isRussianLocaleTag("ru-RU"))
    }

    @Test fun `Russian is not a manual language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguageRules.fromApplicationLanguageTags("ru"))
    }

    @Test fun `unsupported non Russian locale still falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguageRules.effectiveAutomaticLanguage(listOf("sv")))
    }

    @Test fun `Ukrainian remains manually selectable`() {
        assertEquals(AppLanguage.UKRAINIAN, AppLanguageRules.fromApplicationLanguageTags("uk"))
    }

    @Test fun `Russian is not in selectable language list`() {
        assert(!AppLanguageRules.supportedLanguages.any { it.languageTag == "ru" })
    }

    @Test fun `every supported manual language tag round trips`() {
        AppLanguageRules.supportedLanguages.filterNot { it == AppLanguage.AUTOMATIC }.forEach { language ->
            assertEquals(language, AppLanguageRules.fromApplicationLanguageTags(language.languageTag))
        }
    }
}
