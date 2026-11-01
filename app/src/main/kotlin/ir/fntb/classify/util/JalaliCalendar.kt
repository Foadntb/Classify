package ir.fntb.classify.util

import android.icu.util.Calendar
import android.icu.util.ULocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class JalaliDate(val year: Int, val month: Int, val day: Int)

/** تبدیل تاریخ با استفاده از PersianCalendar خود اندروید (بدون کتابخانه خارجی). */
object JalaliCalendar {
    private fun newPersian(): Calendar = Calendar.getInstance(ULocale("fa_IR@calendar=persian"))

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    val weekDays = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    val weekDaysShort = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    fun fromLocalDate(d: LocalDate): JalaliDate {
        val c = newPersian()
        c.timeInMillis = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return JalaliDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate {
        val c = newPersian()
        c.clear()
        c.set(Calendar.YEAR, year)
        c.set(Calendar.MONTH, month - 1)
        c.set(Calendar.DAY_OF_MONTH, day)
        c.set(Calendar.HOUR_OF_DAY, 12)
        return Instant.ofEpochMilli(c.timeInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun firstOfMonth(year: Int, month: Int): Calendar {
        val c = newPersian()
        c.clear()
        c.set(Calendar.YEAR, year)
        c.set(Calendar.MONTH, month - 1)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 12)
        return c
    }

    fun daysInMonth(year: Int, month: Int): Int =
        firstOfMonth(year, month).getActualMaximum(Calendar.DAY_OF_MONTH)

    /** اندیس روز هفتهٔ اول ماه: 0 = شنبه ... 6 = جمعه */
    fun firstWeekdayIndex(year: Int, month: Int): Int =
        firstOfMonth(year, month).get(Calendar.DAY_OF_WEEK) % 7

    fun format(j: JalaliDate): String =
        "${j.day.fa()} ${monthNames[j.month - 1]} ${j.year.fa()}"

    fun format(d: LocalDate): String = format(fromLocalDate(d))

    fun formatWithWeekday(d: LocalDate): String = "${weekDays[d.weekdayIndex()]} ${format(d)}"
}
