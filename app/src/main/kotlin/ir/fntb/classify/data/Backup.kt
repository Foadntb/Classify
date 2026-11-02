package ir.fntb.classify.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

class BackupException(message: String) : Exception(message)

/** اجزایی که در پشتیبان یا بازیابی انتخاب شده‌اند. */
data class BackupParts(
    /** درس‌ها و برنامهٔ هفتگی (ساعت‌های کلاس) */
    val schedule: Boolean = true,
    val homework: Boolean = true,
    val announcements: Boolean = true,
    val students: Boolean = true,
    val photos: Boolean = true,
) {
    val any: Boolean get() = schedule || homework || announcements || students || photos
}

/** محتوای یک فایل پشتیبان */
class BackupFile(
    val className: String,
    val createdAt: Long,
    val data: AppData,
    /** شناسهٔ درس ← عکس JPEG */
    val photos: Map<String, ByteArray>,
    /** (نام، نام خانوادگی) */
    val students: List<Pair<String, String>>,
)

data class MergeResult(
    val data: AppData,
    /** شناسهٔ درس مقصد ← عکسی که باید برایش ثبت شود */
    val photos: Map<String, ByteArray>,
    val addedCourses: Int,
    val addedSlots: Int,
    val addedHomework: Int,
    val addedAnnouncements: Int,
    /** تعداد مواردی که از قبل وجود داشتند و دست نخوردند */
    val alreadyThere: Int,
    /** تکلیف‌هایی که رد شدند چون درسشان در کلاس نیست (و «درس‌ها و برنامه» انتخاب نشده بود) */
    val skippedNoCourse: Int = 0,
)

/**
 * پشتیبان کامل کلاس در یک فایل JSON: درس‌ها (با عکس)، برنامهٔ هفتگی، تکالیف، اعلان‌ها و لیست دانش‌آموزان.
 * بازیابی «ادغام» است: چیزی پاک یا عوض نمی‌شود و فقط موارد نبوده اضافه می‌شوند.
 */
object Backup {
    const val FORMAT = "classify-backup"
    const val VERSION = 1
    const val MAX_FILE_BYTES = 30 * 1024 * 1024
    private const val MAX_PHOTO_BYTES = 1_500_000

    fun toBytes(
        data: AppData,
        photos: Map<String, ByteArray>,
        students: List<Pair<String, String>>,
        now: Long,
        parts: BackupParts = BackupParts(),
    ): ByteArray {
        // نام درس‌ها همیشه می‌آید (اگر تکالیف یا عکس انتخاب شده)، تا هر چیز به درس خودش وصل شود
        val needCourses = parts.schedule || parts.homework || parts.photos
        val chosen = data.copy(
            courses = if (needCourses) data.courses else emptyList(),
            slots = if (parts.schedule) data.slots else emptyList(),
            homework = if (parts.homework) data.homework else emptyList(),
            announcements = if (parts.announcements) data.announcements else emptyList(),
        )
        val o = Storage.toJson(chosen)
        o.remove("classReminder") // تنظیم همین دستگاه است
        val courses = o.getJSONArray("courses")
        for (i in 0 until courses.length()) {
            val c = courses.getJSONObject(i)
            c.remove("photoVer")
            if (parts.photos) {
                photos[c.getString("id")]?.let { c.put("photo", Base64.getEncoder().encodeToString(it)) }
            }
        }
        val hw = o.getJSONArray("homework")
        for (i in 0 until hw.length()) hw.getJSONObject(i).remove("done") // «انجام شد» مخصوص هر گوشی است
        o.put("students", JSONArray().also { arr ->
            if (parts.students) {
                students.forEach { arr.put(JSONObject().put("firstName", it.first).put("lastName", it.second)) }
            }
        })
        o.put("format", FORMAT)
        o.put("version", VERSION)
        o.put("createdAt", now)
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    fun parse(bytes: ByteArray): BackupFile {
        val notBackup = BackupException("این فایل، پشتیبان Classify نیست.")
        val o = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw notBackup
        }
        if (o.optString("format") != FORMAT) throw notBackup
        if (o.optInt("version", 1) > VERSION) {
            throw BackupException("این پشتیبان با نسخهٔ جدیدتر اپ ساخته شده است؛ اپ را به‌روز کنید.")
        }
        val parsed = try {
            Storage.parse(o)
        } catch (e: Exception) {
            throw BackupException("اطلاعات داخل فایل پشتیبان خراب است.")
        }
        val data = sanitize(parsed)

        val photos = mutableMapOf<String, ByteArray>()
        val validIds = data.courses.map { it.id }.toSet()
        val courseArr = o.optJSONArray("courses")
        if (courseArr != null) {
            for (i in 0 until courseArr.length()) {
                val c = courseArr.optJSONObject(i) ?: continue
                val id = c.optString("id")
                val b64 = c.optString("photo")
                if (id !in validIds || b64.isEmpty()) continue
                val bytesJpg = try {
                    Base64.getDecoder().decode(b64)
                } catch (e: IllegalArgumentException) {
                    continue
                }
                val isJpeg = bytesJpg.size in 4..MAX_PHOTO_BYTES &&
                    bytesJpg[0] == 0xFF.toByte() && bytesJpg[1] == 0xD8.toByte() && bytesJpg[2] == 0xFF.toByte()
                if (isJpeg) photos[id] = bytesJpg
            }
        }

        val students = mutableListOf<Pair<String, String>>()
        val stArr = o.optJSONArray("students")
        if (stArr != null) {
            for (i in 0 until stArr.length()) {
                val s = stArr.optJSONObject(i) ?: continue
                val first = s.optString("firstName").trim()
                val last = s.optString("lastName").trim()
                if (first.isNotEmpty() && last.isNotEmpty() && first.length <= 60 && last.length <= 60) {
                    students += first to last
                }
            }
        }
        return BackupFile(
            className = o.optString("className"),
            createdAt = o.optLong("createdAt"),
            data = data,
            photos = photos,
            students = students,
        )
    }

    /** موارد نامعتبر را حذف می‌کند تا سرور بعداً کل داده را رد نکند (سرور حدودی دارد). */
    private fun sanitize(d: AppData): AppData {
        fun idOk(s: String) = s.isNotBlank() && s.length <= 64
        val courses = d.courses
            .filter { idOk(it.id) && it.name.isNotBlank() }
            .distinctBy { it.id }
            .map { it.copy(name = it.name.trim().take(100), teacher = it.teacher.trim().take(100), photoVer = 0) }
        val courseIds = courses.map { it.id }.toSet()
        val slots = d.slots
            .filter {
                idOk(it.id) && it.courseId in courseIds &&
                    it.day in 0..6 && it.startMin in 0..1440 && it.endMin in 0..1440
            }
            .distinctBy { it.id }
            .map { it.copy(room = it.room.trim().take(100)) }
        val homework = d.homework
            .filter { idOk(it.id) && it.courseId in courseIds && it.title.isNotBlank() }
            .distinctBy { it.id }
            .map { it.copy(title = it.title.trim().take(200), details = it.details.take(4000), done = false) }
        val announcements = d.announcements
            .filter { idOk(it.id) && it.text.isNotBlank() }
            .distinctBy { it.id }
            .map { it.copy(text = it.text.trim().take(1000)) }
        return d.copy(courses = courses, slots = slots, homework = homework, announcements = announcements)
    }

    private fun norm(s: String): String = s.trim()
        .replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
        .lowercase()
        .split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ")

    /**
     * ادغام: موارد فایل به داده‌های فعلی اضافه می‌شود.
     * - درسی که با همان شناسه یا همان نام موجود باشد دوباره اضافه نمی‌شود (برنامه و تکالیفش به درس موجود وصل می‌شود).
     * - ساعت، تکلیف و اعلانِ تکراری اضافه نمی‌شود.
     * - عکس فقط برای درسی ثبت می‌شود که عکس ندارد.
     * - [parts] تعیین می‌کند کدام اجزا ادغام شوند. درس‌ها همیشه برای وصل کردن تکالیف و عکس‌ها تطبیق داده می‌شوند،
     *   ولی درسِ تازه فقط وقتی اضافه می‌شود که «درس‌ها و برنامه» انتخاب شده باشد.
     */
    fun merge(
        cur: AppData,
        file: AppData,
        filePhotos: Map<String, ByteArray>,
        parts: BackupParts = BackupParts(),
        hasPhoto: (Course) -> Boolean,
    ): MergeResult {
        val courses = cur.courses.toMutableList()
        val idMap = mutableMapOf<String, String>() // شناسهٔ درس فایل ← شناسهٔ درس مقصد
        val photosOut = mutableMapOf<String, ByteArray>()
        var addedCourses = 0
        var already = 0

        for (fc in file.courses) {
            val existing = courses.firstOrNull { it.id == fc.id }
                ?: courses.firstOrNull { norm(it.name) == norm(fc.name) }
            if (existing != null) {
                idMap[fc.id] = existing.id
                if (parts.schedule) already++
                if (parts.photos && !hasPhoto(existing)) filePhotos[fc.id]?.let { photosOut[existing.id] = it }
            } else if (parts.schedule) {
                courses += fc.copy(photoVer = 0)
                idMap[fc.id] = fc.id
                addedCourses++
                if (parts.photos) filePhotos[fc.id]?.let { photosOut[fc.id] = it }
            }
        }

        val slots = cur.slots.toMutableList()
        var addedSlots = 0
        for (fs in if (parts.schedule) file.slots else emptyList()) {
            val cid = idMap[fs.courseId] ?: continue
            val dup = slots.any {
                it.id == fs.id || (it.courseId == cid && it.day == fs.day && it.startMin == fs.startMin && it.endMin == fs.endMin)
            }
            if (dup) {
                already++
            } else {
                slots += fs.copy(courseId = cid)
                addedSlots++
            }
        }

        val homework = cur.homework.toMutableList()
        var addedHomework = 0
        var skippedNoCourse = 0
        for (fh in if (parts.homework) file.homework else emptyList()) {
            val cid = idMap[fh.courseId]
            if (cid == null) {
                skippedNoCourse++
                continue
            }
            val dup = homework.any {
                it.id == fh.id || (it.courseId == cid && it.due == fh.due && norm(it.title) == norm(fh.title))
            }
            if (dup) {
                already++
            } else {
                homework += fh.copy(courseId = cid, done = false)
                addedHomework++
            }
        }

        val announcements = cur.announcements.toMutableList()
        var addedAnnouncements = 0
        for (fa in if (parts.announcements) file.announcements else emptyList()) {
            val dup = announcements.any { it.id == fa.id || norm(it.text) == norm(fa.text) }
            if (dup) {
                already++
            } else {
                announcements += fa
                addedAnnouncements++
            }
        }

        return MergeResult(
            data = cur.copy(
                courses = courses,
                slots = slots,
                homework = homework,
                announcements = announcements.sortedByDescending { it.createdAt },
            ),
            photos = photosOut,
            addedCourses = addedCourses,
            addedSlots = addedSlots,
            addedHomework = addedHomework,
            addedAnnouncements = addedAnnouncements,
            alreadyThere = already,
            skippedNoCourse = skippedNoCourse,
        )
    }
}
