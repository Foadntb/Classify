package ir.fntb.classify.util

import java.time.LocalDate
import java.util.Locale

fun String.faDigits(): String = map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

fun Int.fa(): String = toString().faDigits()

fun formatTime(min: Int): String =
    String.format(Locale.ENGLISH, "%02d:%02d", min / 60, min % 60).faDigits()

fun formatDuration(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h.fa()} ساعت و ${m.fa()} دقیقه"
        h > 0 -> "${h.fa()} ساعت"
        else -> "${m.fa()} دقیقه"
    }
}

/** 0 = شنبه ... 6 = جمعه */
fun LocalDate.weekdayIndex(): Int = (dayOfWeek.value + 1) % 7

/** ارقام فارسی و عربی را به لاتین تبدیل می‌کند (برای خواندن عددی که کاربر تایپ کرده). */
fun String.enDigits(): String = map {
    when (it) {
        in '۰'..'۹' -> '0' + (it - '۰')
        in '٠'..'٩' -> '0' + (it - '٠')
        else -> it
    }
}.joinToString("")
