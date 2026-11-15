package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IsoDateUtils {

  private const val MILLIS_PER_DAY = 86_400_000L
  private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
  private val isoRegex = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

  fun todayIso(): String {
    val local = Calendar.getInstance()
    return formatYmd(
      local.get(Calendar.YEAR),
      local.get(Calendar.MONTH) + 1,
      local.get(Calendar.DAY_OF_MONTH),
    )
  }

  fun parseYmdOrNull(isoDate: String): Triple<Int, Int, Int>? {
    val match = isoRegex.matchEntire(isoDate.trim()) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    val cal = newUtcCalendar(year, month, day)
    if (
      cal.get(Calendar.YEAR) != year ||
      cal.get(Calendar.MONTH) + 1 != month ||
      cal.get(Calendar.DAY_OF_MONTH) != day
    ) {
      return null
    }
    return Triple(year, month, day)
  }

  fun normalizeOrToday(isoDate: String): String {
    val ymd = parseYmdOrNull(isoDate) ?: return todayIso()
    return formatYmd(ymd.first, ymd.second, ymd.third)
  }

  fun plusDays(isoDate: String, days: Long): String {
    val ymd = parseYmdOrNull(isoDate) ?: parseYmdOrNull(todayIso())!!
    val cal = newUtcCalendar(ymd.first, ymd.second, ymd.third)
    cal.add(Calendar.DAY_OF_YEAR, days.toInt())
    return formatYmd(
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH),
    )
  }

  fun daysBetween(fromIso: String, toIso: String): Long {
    val fromMillis = isoToUtcMillis(fromIso)
    val toMillis = isoToUtcMillis(toIso)
    return (toMillis - fromMillis) / MILLIS_PER_DAY
  }

  fun isBefore(firstIso: String, secondIso: String): Boolean =
    daysBetween(firstIso, secondIso) > 0L

  fun formatShortMonthDay(isoDate: String): String {
    val millis = isoToUtcMillis(isoDate)
    val formatter = SimpleDateFormat("MMM d", Locale.US).apply {
      timeZone = utcTimeZone
    }
    return formatter.format(Date(millis))
  }

  fun isoToUtcMillis(isoDate: String): Long {
    val ymd = parseYmdOrNull(isoDate) ?: parseYmdOrNull(todayIso())!!
    return newUtcCalendar(ymd.first, ymd.second, ymd.third).timeInMillis
  }

  fun utcMillisToIso(millis: Long): String {
    val cal = Calendar.getInstance(utcTimeZone, Locale.US).apply {
      timeInMillis = millis
    }
    return formatYmd(
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH),
    )
  }

  private fun newUtcCalendar(year: Int, month: Int, day: Int): Calendar =
    Calendar.getInstance(utcTimeZone, Locale.US).apply {
      clear()
      set(Calendar.YEAR, year)
      set(Calendar.MONTH, month - 1)
      set(Calendar.DAY_OF_MONTH, day)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

  private fun formatYmd(year: Int, month: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}
