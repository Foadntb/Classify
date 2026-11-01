package ir.fntb.classify.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.fntb.classify.data.AppData
import ir.fntb.classify.data.CLASS_REMINDER_MINUTES
import ir.fntb.classify.data.ClassSlot
import ir.fntb.classify.data.Homework
import ir.fntb.classify.data.ReminderType
import ir.fntb.classify.util.weekdayIndex
import java.time.ZoneId
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL_ID = "reminders"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val KIND_HW = "hw"
    const val KIND_CLASS = "class"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "یادآوری Classify",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "یادآوری شروع کلاس‌ها و مهلت تکالیف" }
        nm.createNotificationChannel(channel)
    }

    fun rescheduleAll(ctx: Context, data: AppData) {
        data.homework.forEach { scheduleHomework(ctx, it) }
        data.slots.forEach {
            if (data.classReminder) scheduleClass(ctx, it) else cancelClass(ctx, it.id)
        }
    }

    // ---------- homework ----------

    fun scheduleHomework(ctx: Context, hw: Homework) {
        cancelHomework(ctx, hw.id)
        if (hw.done || hw.reminder == ReminderType.NONE) return
        val trigger = homeworkTrigger(hw) ?: return
        if (trigger.isAfter(ZonedDateTime.now())) setAt(ctx, KIND_HW, hw.id, trigger)
    }

    fun cancelHomework(ctx: Context, id: String) = cancel(ctx, KIND_HW, id)

    private fun homeworkTrigger(hw: Homework): ZonedDateTime? {
        val local = when (hw.reminder) {
            ReminderType.DAY_BEFORE -> hw.due.minusDays(1).atTime(18, 0)
            ReminderType.DUE_MORNING -> hw.due.atTime(7, 30)
            ReminderType.NONE -> return null
        }
        return local.atZone(ZoneId.systemDefault())
    }

    // ---------- classes ----------

    fun scheduleClass(ctx: Context, slot: ClassSlot) {
        cancelClass(ctx, slot.id)
        setAt(ctx, KIND_CLASS, slot.id, nextClassTrigger(slot))
    }

    fun cancelClass(ctx: Context, id: String) = cancel(ctx, KIND_CLASS, id)

    fun nextClassTrigger(slot: ClassSlot, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val offsetMin = (slot.startMin - CLASS_REMINDER_MINUTES).toLong()
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.weekdayIndex() == slot.day) {
                val t = date.atStartOfDay(now.zone).plusMinutes(offsetMin)
                if (t.isAfter(now)) return t
            }
        }
        return now.plusDays(7)
    }

    // ---------- alarm plumbing ----------

    private fun pending(ctx: Context, kind: String, id: String): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            data = Uri.parse("classify://$kind/$id")
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAt(ctx: Context, kind: String, id: String, at: ZonedDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(ctx, kind, id))
    }

    private fun cancel(ctx: Context, kind: String, id: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, kind, id))
    }
}
