package ir.fntb.classify.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.fntb.classify.data.Storage

/** بعد از روشن شدن گوشی یا به‌روزرسانی اپ، آلارم‌ها دوباره چیده می‌شوند. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.rescheduleAll(ctx, Storage.load(ctx))
    }
}
