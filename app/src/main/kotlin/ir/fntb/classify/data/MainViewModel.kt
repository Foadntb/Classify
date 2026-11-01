package ir.fntb.classify.data

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.fntb.classify.notify.Reminders
import ir.fntb.classify.util.fa
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

data class SyncState(val busy: Boolean = false, val error: String? = null)

data class LoginState(val busy: Boolean = false, val error: String? = null, val needsCode: Boolean = false)

data class ClassesState(
    val busy: Boolean = false,
    val list: List<ClassInfo> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false,
)

data class StudentsState(
    val busy: Boolean = false,
    val list: List<StudentInfo> = emptyList(),
    val error: String? = null,
)

data class PairState(
    val busy: Boolean = false,
    val code: String? = null,
    val seconds: Int = 0,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()

    private val _data = MutableStateFlow(Storage.load(app))
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _session = MutableStateFlow(SessionStore.load(app))
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _login = MutableStateFlow(LoginState())
    val login: StateFlow<LoginState> = _login.asStateFlow()

    private val _classes = MutableStateFlow(ClassesState())
    val classes: StateFlow<ClassesState> = _classes.asStateFlow()

    private val _students = MutableStateFlow(StudentsState())
    val students: StateFlow<StudentsState> = _students.asStateFlow()

    private val _adminClasses = MutableStateFlow(ClassesState())
    val adminClasses: StateFlow<ClassesState> = _adminClasses.asStateFlow()

    private val _pair = MutableStateFlow(PairState())
    val pair: StateFlow<PairState> = _pair.asStateFlow()

    private val _themeMode = MutableStateFlow(SessionStore.themeMode(app))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val syncLock = Mutex()
    private val editCounter = AtomicInteger(0)

    /** true یعنی «مدیر کلاس» (دسترسی ویرایش برنامه و تکالیف) */
    val isAdmin: Boolean get() = _session.value?.role == Role.MANAGER
    val hasUnsynced: Boolean get() = SessionStore.isDirty(ctx)

    init {
        Reminders.rescheduleAll(app, _data.value)
    }

    fun lastValue(key: String): String = SessionStore.lastValue(ctx, key)

    fun consumeMessage() {
        _message.value = null
    }

    /** ذخیرهٔ محلی بدون علامت‌گذاری «تغییر ارسال‌نشده». */
    private fun commit(new: AppData) {
        _data.value = new
        viewModelScope.launch(Dispatchers.IO) { Storage.save(ctx, new) }
    }

    /** تغییر توسط مدیر کلاس: ذخیره + علامت dirty + ارسال به سرور. */
    private fun commitEdit(new: AppData) {
        editCounter.incrementAndGet()
        SessionStore.setDirty(ctx, true)
        commit(new)
        refresh()
    }

    private fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> {
        val i = indexOfFirst { id(it) == id(item) }
        return if (i < 0) this + item else toMutableList().also { it[i] = item }
    }

    /** پیام خطا را برمی‌گرداند؛ اگر نشست باطل شده باشد به صفحهٔ ورود برمی‌گردد. */
    private fun handleFailure(e: Exception): String {
        if (e is ApiException) {
            val msg = Api.message(e)
            if (Api.isSessionDead(e)) {
                logout()
                _login.value = LoginState(error = msg)
            }
            return msg
        }
        return "خطای ناشناخته"
    }

    // ---------- login ----------

    fun loadClasses() {
        viewModelScope.launch {
            _classes.value = _classes.value.copy(busy = true, error = null)
            try {
                val base = ServerConfig.base()
                _classes.value = ClassesState(list = Api.classes(base), loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _classes.value = ClassesState(error = if (e is ApiException) Api.message(e) else "خطای ناشناخته")
            }
        }
    }

    private fun launchLogin(keepCode: Boolean, block: suspend () -> Unit) {
        viewModelScope.launch {
            _login.value = LoginState(busy = true)
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val key = (e as? ApiException)?.key.orEmpty()
                val needsCode = key == "device_locked" || (keepCode && (key == "bad_code" || key == "too_many_attempts"))
                _login.value = LoginState(error = if (e is ApiException) Api.message(e) else "خطای ناشناخته", needsCode = needsCode)
            }
        }
    }

    private suspend fun completeLogin(base: String, role: Role, classId: String, r: LoginResult) {
        val s = Session(base, role, r.token, classId, r.className, r.name)
        if (role != Role.SUPER) {
            val remote = Api.fetch(s)
            resetLocalData()
            SessionStore.setDirty(ctx, false)
            applyRemote(remote)
        }
        SessionStore.save(ctx, s)
        _sync.value = SyncState()
        _session.value = s
        _login.value = LoginState()
    }

    fun loginSuper(password: String) = launchLogin(false) {
        val base = ServerConfig.base()
        completeLogin(base, Role.SUPER, "", Api.loginSuper(base, password.trim()))
    }

    fun loginManager(classId: String, password: String) = launchLogin(false) {
        val base = ServerConfig.base()
        val r = Api.loginManager(base, classId, password.trim())
        SessionStore.rememberLogin(ctx, classId, lastValue("first"), lastValue("last"))
        completeLogin(base, Role.MANAGER, classId, r)
    }

    fun loginStudent(classId: String, first: String, last: String) = launchLogin(false) {
        val base = ServerConfig.base()
        val r = Api.loginStudent(base, classId, first.trim(), last.trim(), SessionStore.deviceId(ctx))
        SessionStore.rememberLogin(ctx, classId, first.trim(), last.trim())
        completeLogin(base, Role.STUDENT, classId, r)
    }

    fun verifyStudent(classId: String, first: String, last: String, code: String) =
        launchLogin(true) {
            val base = ServerConfig.base()
            val r = Api.verifyStudent(base, classId, first.trim(), last.trim(), SessionStore.deviceId(ctx), code.trim())
            SessionStore.rememberLogin(ctx, classId, first.trim(), last.trim())
            completeLogin(base, Role.STUDENT, classId, r)
        }

    fun cancelCode() {
        _login.value = LoginState()
    }

    private fun resetLocalData() {
        val d = _data.value
        d.slots.forEach { Reminders.cancelClass(ctx, it.id) }
        d.homework.forEach { Reminders.cancelHomework(ctx, it.id) }
        PendingPhotos.clearAll(ctx)
        PhotoCache.clearAll(ctx)
        commit(AppData(classReminder = d.classReminder))
    }

    fun logout() {
        resetLocalData()
        SessionStore.clear(ctx)
        _sync.value = SyncState()
        _login.value = LoginState()
        _students.value = StudentsState()
        _adminClasses.value = ClassesState()
        _pair.value = PairState()
        _session.value = null
    }

    // ---------- sync ----------

    fun refresh() {
        viewModelScope.launch { syncLock.withLock { syncLocked() } }
    }

    private suspend fun syncLocked() {
        val s = _session.value ?: return
        if (s.role == Role.SUPER) return
        _sync.value = _sync.value.copy(busy = true)
        try {
            if (s.role == Role.MANAGER) {
                if (SessionStore.isDirty(ctx)) {
                    val snapshot = _data.value
                    val mark = editCounter.get()
                    Api.push(s, snapshot)
                    if (editCounter.get() == mark) SessionStore.setDirty(ctx, false)
                }
                uploadPendingPhotos(s)
            }
            val mark = editCounter.get()
            val remote = Api.fetch(s)
            // اگر در حین دریافت، مدیر چیزی را تغییر داده، دادهٔ سرور را نادیده بگیر؛ نوبت بعدیِ sync آن را می‌فرستد
            if (editCounter.get() == mark && !(s.role == Role.MANAGER && SessionStore.isDirty(ctx))) {
                applyRemote(remote)
            }
            _sync.value = SyncState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFailure(e)
            if (_session.value != null) _sync.value = SyncState(error = msg)
        }
    }

    private suspend fun uploadPendingPhotos(s: Session) {
        for (f in PendingPhotos.list(ctx)) {
            try {
                Api.uploadPhoto(s, f.nameWithoutExtension, f.readBytes())
                f.delete()
            } catch (e: ApiException) {
                // ۴۰۴ = درس هنوز روی سرور نیست (بعداً دوباره تلاش می‌شود) یا حذف شده است
                if (e.code == 404) {
                    if (!SessionStore.isDirty(ctx)) f.delete()
                } else {
                    throw e
                }
            }
        }
    }

    /** دادهٔ سرور را می‌گیرد؛ وضعیت «انجام شد» و تنظیمات دستگاه محلی می‌مانند. */
    private fun applyRemote(remote: AppData) {
        val old = _data.value
        val doneIds = old.homework.filter { it.done }.map { it.id }.toSet()
        val merged = remote.copy(
            homework = remote.homework.map { it.copy(done = it.id in doneIds) },
            classReminder = old.classReminder,
        )
        val slotIds = merged.slots.map { it.id }.toSet()
        val hwIds = merged.homework.map { it.id }.toSet()
        old.slots.filter { it.id !in slotIds }.forEach { Reminders.cancelClass(ctx, it.id) }
        old.homework.filter { it.id !in hwIds }.forEach { Reminders.cancelHomework(ctx, it.id) }
        commit(merged)
        Reminders.rescheduleAll(ctx, merged)
    }

    // ---------- photos ----------

    suspend fun loadPhoto(courseId: String, ver: Int): ImageBitmap? =
        PhotoCache.load(ctx, _session.value, courseId, ver)

    /** برای CompositionLocal عکس درس‌ها */
    val photoLoader: suspend (String, Int) -> ImageBitmap? = { id, ver -> loadPhoto(id, ver) }

    // ---------- courses (مدیر کلاس) ----------

    fun saveCourse(c: Course, photo: ByteArray? = null) {
        if (!isAdmin) return
        val d = _data.value
        if (photo != null) PendingPhotos.put(ctx, c.id, photo)
        commitEdit(d.copy(courses = d.courses.upsert(c) { it.id }))
    }

    fun deleteCourse(id: String) {
        if (!isAdmin) return
        val d = _data.value
        d.slots.filter { it.courseId == id }.forEach { Reminders.cancelClass(ctx, it.id) }
        d.homework.filter { it.courseId == id }.forEach { Reminders.cancelHomework(ctx, it.id) }
        PendingPhotos.delete(ctx, id)
        commitEdit(
            d.copy(
                courses = d.courses.filter { it.id != id },
                slots = d.slots.filter { it.courseId != id },
                homework = d.homework.filter { it.courseId != id },
            )
        )
    }

    // ---------- class slots (مدیر کلاس) ----------

    fun saveSlot(s: ClassSlot) {
        if (!isAdmin) return
        val d = _data.value
        commitEdit(d.copy(slots = d.slots.upsert(s) { it.id }))
        if (d.classReminder) Reminders.scheduleClass(ctx, s)
    }

    fun deleteSlot(id: String) {
        if (!isAdmin) return
        val d = _data.value
        Reminders.cancelClass(ctx, id)
        commitEdit(d.copy(slots = d.slots.filter { it.id != id }))
    }

    // ---------- homework ----------

    fun saveHomework(h: Homework) {
        if (!isAdmin) return
        val d = _data.value
        commitEdit(d.copy(homework = d.homework.upsert(h) { it.id }))
        Reminders.scheduleHomework(ctx, h)
    }

    /** «انجام شد» فقط روی همین دستگاه ذخیره می‌شود؛ برای همه‌ی نقش‌ها مجاز است. */
    fun toggleHomework(id: String) {
        val d = _data.value
        val h = d.homework.firstOrNull { it.id == id } ?: return
        val updated = h.copy(done = !h.done)
        commit(d.copy(homework = d.homework.map { if (it.id == id) updated else it }))
        Reminders.scheduleHomework(ctx, updated)
    }

    fun deleteHomework(id: String) {
        if (!isAdmin) return
        val d = _data.value
        Reminders.cancelHomework(ctx, id)
        commitEdit(d.copy(homework = d.homework.filter { it.id != id }))
    }

    // ---------- announcements (مدیر کلاس) ----------

    fun addAnnouncement(text: String) {
        if (!isAdmin || text.isBlank()) return
        val d = _data.value
        val a = Announcement(newId(), text.trim(), System.currentTimeMillis())
        commitEdit(d.copy(announcements = listOf(a) + d.announcements))
    }

    fun deleteAnnouncement(id: String) {
        if (!isAdmin) return
        val d = _data.value
        commitEdit(d.copy(announcements = d.announcements.filter { it.id != id }))
    }

    // ---------- students (مدیر کلاس) ----------

    fun loadStudents() {
        val s = _session.value ?: return
        if (s.role != Role.MANAGER) return
        viewModelScope.launch {
            _students.value = _students.value.copy(busy = true, error = null)
            try {
                _students.value = StudentsState(list = Api.students(s))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = handleFailure(e)
                _students.value = _students.value.copy(busy = false, error = msg)
            }
        }
    }

    /** هر خط یک دانش‌آموز؛ جداکنندهٔ نام و نام‌خانوادگی: «،» «,» «;» یا tab، وگرنه آخرین فاصله. */
    private fun parseNames(text: String): Pair<List<Pair<String, String>>, Int> {
        val ok = mutableListOf<Pair<String, String>>()
        var bad = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val sep = line.indexOfFirst { it == '،' || it == ',' || it == ';' || it == '\t' }
            val first: String
            val last: String
            if (sep >= 0) {
                first = line.substring(0, sep).trim()
                last = line.substring(sep + 1).trim()
            } else {
                val sp = line.lastIndexOf(' ')
                if (sp < 0) {
                    bad++
                    continue
                }
                first = line.substring(0, sp).trim()
                last = line.substring(sp + 1).trim()
            }
            if (first.isEmpty() || last.isEmpty()) bad++ else ok += first to last
        }
        return ok to bad
    }

    fun addStudents(text: String) {
        val s = _session.value ?: return
        val (names, bad) = parseNames(text)
        if (names.isEmpty()) {
            _message.value = "نامی برای افزودن پیدا نشد. هر خط یک دانش‌آموز: «نام نام‌خانوادگی»"
            return
        }
        viewModelScope.launch {
            try {
                val r = Api.addStudents(s, names)
                _message.value = buildString {
                    append("${r.added.fa()} نفر اضافه شد")
                    if (r.duplicates.isNotEmpty()) append("؛ تکراری: ${r.duplicates.joinToString("، ")}")
                    val inv = r.invalid + bad
                    if (inv > 0) append("؛ نامعتبر: ${inv.fa()}")
                }
                loadStudents()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = handleFailure(e)
            }
        }
    }

    private fun studentAction(done: String?, block: suspend (Session) -> Unit) {
        val s = _session.value ?: return
        viewModelScope.launch {
            try {
                block(s)
                if (done != null) _message.value = done
                loadStudents()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = handleFailure(e)
            }
        }
    }

    fun deleteStudent(id: String) = studentAction("دانش‌آموز حذف شد") { Api.deleteStudent(it, id) }

    fun releaseStudent(id: String) =
        studentAction("قفل دستگاه آزاد شد؛ دانش‌آموز می‌تواند از هر دستگاهی وارد شود") { Api.releaseStudent(it, id) }

    /** minutes = 0 یعنی رفع مسدودیت */
    fun blockStudent(id: String, minutes: Int) = studentAction(
        if (minutes > 0) "دانش‌آموز مسدود شد" else "مسدودیت برداشته شد",
    ) { Api.blockStudent(it, id, minutes) }

    // ---------- device pairing (دانش‌آموز) ----------

    fun requestPairCode() {
        val s = _session.value ?: return
        viewModelScope.launch {
            _pair.value = PairState(busy = true)
            try {
                val (code, secs) = Api.pairCode(s)
                _pair.value = PairState(code = code, seconds = secs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pair.value = PairState(error = handleFailure(e))
            }
        }
    }

    fun clearPairCode() {
        _pair.value = PairState()
    }

    // ---------- classes (مدیر کل) ----------

    fun loadAdminClasses() {
        val s = _session.value ?: return
        if (s.role != Role.SUPER) return
        viewModelScope.launch {
            _adminClasses.value = _adminClasses.value.copy(busy = true, error = null)
            try {
                _adminClasses.value = ClassesState(list = Api.adminClasses(s), loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = handleFailure(e)
                _adminClasses.value = _adminClasses.value.copy(busy = false, error = msg)
            }
        }
    }

    private fun adminAction(onDone: (String?) -> Unit, block: suspend (Session) -> Unit) {
        val s = _session.value ?: return
        viewModelScope.launch {
            try {
                block(s)
                onDone(null)
                loadAdminClasses()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone(handleFailure(e))
            }
        }
    }

    /** onDone: null = موفق، وگرنه متن خطا */
    fun createClass(name: String, password: String, onDone: (String?) -> Unit) =
        adminAction(onDone) { Api.createClass(it, name.trim(), password) }

    fun updateClass(id: String, name: String, password: String?, onDone: (String?) -> Unit) =
        adminAction(onDone) { Api.updateClass(it, id, name.trim(), password) }

    fun deleteClass(id: String, onDone: (String?) -> Unit) =
        adminAction(onDone) { Api.deleteClass(it, id) }

    // ---------- settings ----------

    fun setClassReminder(on: Boolean) {
        val d = _data.value.copy(classReminder = on)
        commit(d)
        d.slots.forEach { if (on) Reminders.scheduleClass(ctx, it) else Reminders.cancelClass(ctx, it.id) }
    }

    fun setThemeMode(m: ThemeMode) {
        SessionStore.setThemeMode(ctx, m)
        _themeMode.value = m
    }
}
