package com.jpdrw.household.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

object DateUtils {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): String = LocalDate.now().format(isoFormatter)

    fun isoWeek(date: LocalDate = LocalDate.now()): String {
        val weekFields = WeekFields.of(Locale.getDefault())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    fun startOfMonth(date: LocalDate = LocalDate.now()): String =
        date.withDayOfMonth(1).format(isoFormatter)

    fun endOfMonth(date: LocalDate = LocalDate.now()): String =
        date.withDayOfMonth(date.lengthOfMonth()).format(isoFormatter)
}
