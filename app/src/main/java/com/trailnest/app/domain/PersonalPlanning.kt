package com.trailnest.app.domain

import java.time.*

data class ChecklistItem(val id: String, val title: String, val checked: Boolean = false, val defaultIndex: Int? = null)
object PersonalChecklist {
    val defaults = (0..10).map { "checklist_default_$it" }
    fun initial(checked: (Int) -> Boolean = { false }) = defaults.mapIndexed { i, title -> ChecklistItem("kit_$i", title, checked(i), i) }
    fun validateTitle(title: String): String = title.trim().also { require(it.isNotEmpty() && it.length <= 120) { throw TextFailure("error_use_an_item_name_between_1_and_120_characters") } }
    fun reset(items: List<ChecklistItem>) = items.map { it.copy(checked = false) }
}
data class ActivityEntry(val date: Long, val metres: Double, val minutes: Int)
data class ActivityTotal(val walks: Int, val metres: Double, val minutes: Long)
object JournalRules {
    fun decimal(text: String, locale: java.util.Locale = java.util.Locale.getDefault()): Double? {
        val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val normalized = text.trim().map { char -> when { char == separator -> '.'; Character.digit(char, 10) >= 0 -> '0' + Character.digit(char, 10); else -> char } }.joinToString("")
        return normalized.takeIf { Regex("[+-]?[0-9]+(?:\\.[0-9]+)?").matches(it) }?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    fun editableDistance(metres: Double, imperial: Boolean, locale: java.util.Locale = java.util.Locale.getDefault()): String =
        java.text.NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 4 }.format(metres / if (imperial) 1609.344 else 1000.0)
    fun editedMetres(text: String, imperial: Boolean, original: Double?, locale: java.util.Locale = java.util.Locale.getDefault()): Double =
        if (original != null && decimal(text, locale) == decimal(editableDistance(original, imperial, locale), locale)) original else requireNotNull(decimal(text, locale)) * if (imperial) 1609.344 else 1000.0
    fun date(text: String, today: LocalDate = LocalDate.now()): LocalDate = LocalDate.parse(text).also {
        require(!it.isAfter(today)) { throw TextFailure("error_a_completed_walk_cannot_be_dated_in_the_future") }
        require(it.year >= 1970) { throw TextFailure("error_choose_a_date_from_1970_onwards") }
    }
    fun validate(title: String, distance: String, minutes: String, locale: java.util.Locale = java.util.Locale.getDefault()) {
        val d = decimal(distance, locale); val m = minutes.toIntOrNull()
        require(title.trim().isNotEmpty() && title.trim().length <= 120) { throw TextFailure("error_enter_a_walk_title_of_up_to_120_characters") }
        require(d != null && d.isFinite() && d >= 0 && d <= 1000) { throw TextFailure("error_enter_a_distance_from_zero_to_1_000_zero_means_no_distance_was_me") }
        require(m != null && m in 1..10080) { throw TextFailure("error_enter_a_duration_between_1_and_10_080_minutes") }
    }
    fun total(entries: List<ActivityEntry>) = ActivityTotal(entries.size, entries.sumOf { it.metres }, entries.sumOf { it.minutes.toLong() })
    fun year(entries: List<ActivityEntry>, year: Int, zone: ZoneId) = total(entries.filter { Instant.ofEpochMilli(it.date).atZone(zone).year == year })
    fun months(entries: List<ActivityEntry>, year: Int, zone: ZoneId): List<ActivityTotal> = (1..12).map { month ->
        total(entries.filter { val date = Instant.ofEpochMilli(it.date).atZone(zone); date.year == year && date.monthValue == month })
    }
}
data class SharedWalkPlan(val title: String, val start: GeoPoint, val distance: String, val estimatedMinutes: Int, val expectedReturn: ZonedDateTime, val contact: String) {
    companion object {
        fun parseReturn(text: String, zone: ZoneId): ZonedDateTime {
            val local = LocalDateTime.parse(text)
            val offsets = zone.rules.getValidOffsets(local)
            require(offsets.size == 1) { throw TextFailure("error_choose_a_return_time_outside_a_daylight_saving_clock_change") }
            return ZonedDateTime.ofStrict(local, offsets.single(), zone)
        }
    }
    fun text(template: String, contactTemplate: String, now: Instant = Instant.now(), locale: java.util.Locale = java.util.Locale.getDefault()): String {
        require(expectedReturn.toInstant().isAfter(now)) { throw TextFailure("error_choose_an_expected_return_time_in_the_future") }
        require(estimatedMinutes in 1..10080) { throw TextFailure("error_enter_an_estimated_duration_between_1_and_10_080_minutes") }
        require(contact.length <= 500) { throw TextFailure("error_contact_details_must_be_500_characters_or_fewer") }
        return String.format(locale, template, title, DirectionalText.ltr("${start.lat}, ${start.lon}"), distance, estimatedMinutes,
            DirectionalText.ltr(expectedReturn.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm XXX '['VV']'"))),
            if (contact.isBlank()) "" else String.format(locale, contactTemplate, contact.trim()))
    }
}
