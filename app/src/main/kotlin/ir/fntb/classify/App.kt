package ir.fntb.classify

import android.app.Application
import ir.fntb.classify.notify.Reminders

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
    }
}
