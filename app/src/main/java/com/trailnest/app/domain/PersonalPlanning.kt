package com.trailnest.app.domain

import java.time.*

data class ChecklistItem(val id: String, val title: String, val checked: Boolean = false)
object PersonalChecklist {
    val defaults = listOf("Drinking water", "Suitable footwear", "Weather-appropriate clothing", "Charged phone", "Power bank", "Snacks", "Offline route information", "Check current conditions", "Emergency contact plan", "Dog lead, if relevant", "First-aid essentials")
    fun initial(checked: (Int) -> Boolean = { false }) = defaults.mapIndexed { i, title -> ChecklistItem("kit_$i", title, checked(i)) }
    fun validateTitle(title: String): String = title.trim().also { require(it.isNotEmpty() && it.length <= 120) { "Use an item name between 1 and 120 characters." } }
    fun reset(items: List<ChecklistItem>) = items.map { it.copy(checked = false) }
}
data class ActivityEntry(val date: Long, val metres: Double, val minutes: Int)
data class ActivityTotal(val walks: Int, val metres: Double, val minutes: Long)
object JournalRules {
    fun editableDistance(metres: Double, imperial: Boolean): String = (metres / if (imperial) 1609.344 else 1000.0).toBigDecimal().setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    fun editedMetres(text: String, imperial: Boolean, original: Double?): Double =
        if (original != null && text == editableDistance(original, imperial)) original else text.toDouble() * if (imperial) 1609.344 else 1000.0
    fun date(text: String, today: LocalDate = LocalDate.now()): LocalDate = LocalDate.parse(text).also {
        require(!it.isAfter(today)) { "A completed walk cannot be dated in the future." }
        require(it.year >= 1970) { "Choose a date from 1970 onwards." }
    }
    fun validate(title: String, distance: String, minutes: String) {
        val d = distance.toDoubleOrNull(); val m = minutes.toIntOrNull()
        require(title.trim().isNotEmpty() && title.trim().length <= 120) { "Enter a walk title of up to 120 characters." }
        require(d != null && d.isFinite() && d > 0 && d <= 1000) { "Enter a positive distance of up to 1,000." }
        require(m != null && m in 1..10080) { "Enter a duration between 1 and 10,080 minutes." }
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
            require(offsets.size == 1) { "Choose a return time outside a daylight-saving clock change." }
            return ZonedDateTime.ofStrict(local, offsets.single(), zone)
        }
    }
    fun text(now: Instant = Instant.now()): String {
        require(expectedReturn.toInstant().isAfter(now)) { "Choose an expected return time in the future." }
        require(estimatedMinutes in 1..10080) { "Enter an estimated duration between 1 and 10,080 minutes." }
        require(contact.length <= 500) { "Contact details must be 500 characters or fewer." }
        return "TrailNest walk plan\n$title\nStart: ${start.lat}, ${start.lon}\nDistance: $distance\nEstimated duration: $estimatedMinutes min (personal estimate)\nExpected return: ${expectedReturn.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm XXX '['VV']'"))}" +
            (if (contact.isBlank()) "" else "\nOptional contact: ${contact.trim()}") + "\nAccess unreviewed. GPX geometry can be exported separately. Static plan only; no live tracking or emergency monitoring."
    }
}
