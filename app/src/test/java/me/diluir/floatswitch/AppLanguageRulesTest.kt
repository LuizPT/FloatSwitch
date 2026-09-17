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

    @Test fun `every supported manual language tag round trips`() {
        AppLanguageRules.supportedLanguages.filterNot { it == AppLanguage.AUTOMATIC }.forEach { language ->
            assertEquals(language, AppLanguageRules.fromApplicationLanguageTags(language.languageTag))
        }
    }
}
