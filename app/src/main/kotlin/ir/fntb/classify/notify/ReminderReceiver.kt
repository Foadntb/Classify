package ir.fntb.classify.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import ir.fntb.classify.MainActivity
import ir.fntb.classify.R
import ir.fntb.classify.data.CLASS_REMINDER_MINUTES
import ir.fntb.classify.data.Storage
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.formatTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val kind = intent.getStringExtra(Reminders.EXTRA_KIND) ?: return
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        val data = Storage.load(ctx)

        when (kind) {
            Reminders.KIND_HW -> {
                val hw = data.homework.firstOrNull { it.id == id } ?: return
                if (hw.done) return
                val course = data.courses.firstOrNull { it.id == hw.courseId }
                Notifier.show(
                    ctx,
                    id.hashCode(),
                    "تکلیف ${course?.name.orEmpty()}".trim(),
                    "${hw.title}\nمهلت: ${JalaliCalendar.formatWithWeekday(hw.due)}",
                )
            }

            Reminders.KIND_CLASS -> {
                val slot = data.slots.firstOrNull { it.id == id } ?: return
                if (!data.classReminder) return
                val course = data.courses.firstOrNull { it.id == slot.courseId }
                val room = if (slot.room.isNotBlank()) " — ${slot.room}" else ""
                Notifier.show(
                    ctx,
                    id.hashCode(),
                    "کلاس ${course?.name.orEmpty()}".trim(),
                    "${CLASS_REMINDER_MINUTES.fa()} دقیقهٔ دیگر، ساعت ${formatTime(slot.startMin)}$room",
                )
                // کلاس‌ها هفتگی تکرار می‌شوند؛ نوبت بعدی را بچین
                Reminders.scheduleClass(ctx, slot)
            }
        }
    }
}

object Notifier {
    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        Reminders.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
