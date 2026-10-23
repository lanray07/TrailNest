package com.trailnest.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LocalisedInputTest {
    @Test fun decimalCommaAndArabicDigitsAreAccepted() {
        assertEquals(2.5, JournalRules.decimal("2,5", Locale.FRANCE)!!, 0.0)
        assertEquals(2.5, JournalRules.decimal("٢٫٥", Locale.forLanguageTag("ar"))!!, 0.0)
        assertEquals(2.5, JournalRules.decimal("२.५", Locale.forLanguageTag("hi"))!!, 0.0)
        JournalRules.validate("Marche", "2,5", "30", Locale.FRANCE)
    }
    @Test fun groupingMixedSeparatorsAndNonFiniteValuesAreRejected() {
        for (value in listOf("1,234.5", "1 234", "NaN", "Infinity", "1e9", "-Infinity"))
            assertNull(JournalRules.decimal(value, Locale.FRANCE))
    }
    @Test fun localizedEditingPreservesExactStoredDistance() {
        val exact = 1234.567891
        for (locale in listOf(Locale.FRANCE, Locale.GERMANY, Locale.forLanguageTag("ar"), Locale.forLanguageTag("hi"))) {
            val text = JournalRules.editableDistance(exact, false, locale)
            assertEquals(exact, JournalRules.editedMetres(text, false, exact, locale), 0.0)
        }
        assertEquals(2500.0, JournalRules.editedMetres("2,5", false, exact, Locale.FRANCE), 0.0)
        // Saved editor state can contain the previous locale's separator after recreation.
        assertEquals(exact, JournalRules.editedMetres(JournalRules.editableDistance(exact, false, Locale.US), false, exact, Locale.FRANCE), 0.0)
    }
    @Test fun domainValidationExposesAStableMessageKey() {
        val failure = runCatching { PersonalChecklist.validateTitle(" ") }.exceptionOrNull()
        assertTrue(failure is TextFailure)
        assertEquals("error_use_an_item_name_between_1_and_120_characters", (failure as TextFailure).resourceName)
    }
}
