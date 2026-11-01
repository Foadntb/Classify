package ir.fntb.classify.data

import java.time.LocalDate
import java.util.UUID

const val CLASS_REMINDER_MINUTES = 15

fun newId(): String = UUID.randomUUID().toString()

data class Course(
    val id: String,
    val name: String,
    val teacher: String,
    val colorIndex: Int,
    /** 0 = بدون عکس؛ در غیر این صورت نسخهٔ عکس روی سرور (برای کش) */
    val photoVer: Int = 0,
)

/** day: 0 = شنبه ... 6 = جمعه */
data class ClassSlot(
    val id: String,
    val courseId: String,
    val day: Int,
    val startMin: Int,
    val endMin: Int,
    val room: String,
)

enum class Priority(val label: String) {
    LOW("کم"),
    NORMAL("متوسط"),
    HIGH("زیاد"),
}

enum class ReminderType(val label: String) {
    NONE("بدون یادآوری"),
    DAY_BEFORE("شب قبل از مهلت (ساعت ۱۸:۰۰)"),
    DUE_MORNING("صبح روز مهلت (ساعت ۰۷:۳۰)"),
}

data class Homework(
    val id: String,
    val courseId: String,
    val title: String,
    val details: String,
    val due: LocalDate,
    val priority: Priority,
    val reminder: ReminderType,
    val done: Boolean,
)

/** createdAt: میلی‌ثانیه (epoch) */
data class Announcement(
    val id: String,
    val text: String,
    val createdAt: Long,
)

data class AppData(
    val courses: List<Course> = emptyList(),
    val slots: List<ClassSlot> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val classReminder: Boolean = true,
    val className: String = "",
)

/** blockedUntil: ثانیهٔ epoch؛ 0 یعنی مسدود نیست. */
data class StudentInfo(
    val id: String,
    val firstName: String,
    val lastName: String,
    val deviceLocked: Boolean,
    val blockedUntil: Long,
) {
    val fullName: String get() = "$firstName $lastName"
}

data class ClassInfo(
    val id: String,
    val name: String,
    val students: Int = 0,
)

enum class ThemeMode(val label: String) {
    SYSTEM("سیستم"),
    LIGHT("روشن"),
    DARK("تاریک"),
}
