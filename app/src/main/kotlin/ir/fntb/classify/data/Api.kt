package ir.fntb.classify.data

import ir.fntb.classify.util.fa
import ir.fntb.classify.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** code == 0 یعنی خطای شبکه؛ code < 0 خطای محلی. در غیر این صورت message همان بدنهٔ JSON خطای سرور است. */
class ApiException(val code: Int, message: String) : Exception(message) {
    private val json: JSONObject? = try {
        JSONObject(message)
    } catch (e: Exception) {
        null
    }

    /** کلید خطای سرور مثل wrong_secret یا not_in_list */
    val key: String get() = json?.optString("error").orEmpty()
    val seconds: Long get() = json?.optLong("seconds") ?: 0L
}

data class LoginResult(val token: String, val className: String, val name: String)

data class AddResult(val added: Int, val duplicates: List<String>, val invalid: Int)

object Api {
    private fun raw(
        base: String,
        path: String,
        method: String,
        token: String?,
        body: ByteArray?,
        contentType: String?,
    ): ByteArray {
        val conn = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ApiException(-1, "آدرس سرور نامعتبر است")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) throw ApiException(code, String(bytes, Charsets.UTF_8))
            return bytes
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(0, e.message ?: "network")
        } finally {
            conn.disconnect()
        }
    }

    private fun json(base: String, path: String, method: String, token: String?, body: JSONObject?): JSONObject {
        val bytes = raw(base, path, method, token, body?.toString()?.toByteArray(Charsets.UTF_8), null)
        return try {
            if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ApiException(-2, "این آدرس سرور «Classify» نیست")
        }
    }

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        List(length()) { f(getJSONObject(it)) }

    // ---------- public / login ----------

    suspend fun classes(base: String): List<ClassInfo> = withContext(Dispatchers.IO) {
        val o = json(base, "/api/classes", "GET", null, null)
        try {
            o.getJSONArray("classes").mapObjects { ClassInfo(it.getString("id"), it.getString("name")) }
        } catch (e: Exception) {
            throw ApiException(-2, "این آدرس سرور «Classify» نیست")
        }
    }

    private fun loginResult(o: JSONObject): LoginResult = try {
        LoginResult(o.getString("token"), o.optString("className"), o.optString("name"))
    } catch (e: Exception) {
        throw ApiException(-2, "این آدرس سرور «Classify» نیست")
    }

    suspend fun loginSuper(base: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        loginResult(json(base, "/api/login/super", "POST", null, JSONObject().put("password", password)))
    }

    suspend fun loginManager(base: String, classId: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            loginResult(
                json(
                    base, "/api/login/manager", "POST", null,
                    JSONObject().put("classId", classId).put("password", password),
                )
            )
        }

    private fun studentBody(classId: String, first: String, last: String, deviceId: String) = JSONObject()
        .put("classId", classId)
        .put("firstName", first)
        .put("lastName", last)
        .put("deviceId", deviceId)

    suspend fun loginStudent(base: String, classId: String, first: String, last: String, deviceId: String): LoginResult =
        withContext(Dispatchers.IO) {
            loginResult(json(base, "/api/login/student", "POST", null, studentBody(classId, first, last, deviceId)))
        }

    suspend fun verifyStudent(
        base: String, classId: String, first: String, last: String, deviceId: String, code: String,
    ): LoginResult = withContext(Dispatchers.IO) {
        loginResult(
            json(base, "/api/student/verify", "POST", null, studentBody(classId, first, last, deviceId).put("code", code))
        )
    }

    // ---------- class data ----------

    suspend fun fetch(s: Session): AppData = withContext(Dispatchers.IO) {
        val o = json(s.serverUrl, "/api/class/data", "GET", s.token, null)
        try {
            Storage.parse(o)
        } catch (e: Exception) {
            throw ApiException(-2, "پاسخ سرور نامعتبر است")
        }
    }

    suspend fun push(s: Session, d: AppData) = withContext(Dispatchers.IO) {
        raw(s.serverUrl, "/api/class/data", "PUT", s.token, Storage.toJson(d).toString().toByteArray(Charsets.UTF_8), null)
        Unit
    }

    suspend fun uploadPhoto(s: Session, courseId: String, jpeg: ByteArray): Int = withContext(Dispatchers.IO) {
        val bytes = raw(s.serverUrl, "/api/class/courses/$courseId/photo", "PUT", s.token, jpeg, "image/jpeg")
        try {
            JSONObject(String(bytes, Charsets.UTF_8)).getInt("photoVer")
        } catch (e: Exception) {
            0
        }
    }

    suspend fun downloadPhoto(s: Session, courseId: String): ByteArray = withContext(Dispatchers.IO) {
        raw(s.serverUrl, "/api/class/courses/$courseId/photo", "GET", s.token, null, null)
    }

    // ---------- device pairing (student) ----------

    /** کد تأیید ۶ رقمی برای افزودن دستگاه جدید؛ (کد، مدت اعتبار به ثانیه) */
    suspend fun pairCode(s: Session): Pair<String, Int> = withContext(Dispatchers.IO) {
        val o = json(s.serverUrl, "/api/student/pair-code", "POST", s.token, JSONObject())
        Pair(o.getString("code"), o.optInt("expiresIn", 120))
    }

    // ---------- students (manager) ----------

    suspend fun students(s: Session): List<StudentInfo> = withContext(Dispatchers.IO) {
        json(s.serverUrl, "/api/class/students", "GET", s.token, null)
            .getJSONArray("students")
            .mapObjects {
                StudentInfo(
                    id = it.getString("id"),
                    firstName = it.getString("firstName"),
                    lastName = it.getString("lastName"),
                    deviceLocked = it.optBoolean("deviceLocked"),
                    blockedUntil = it.optLong("blockedUntil"),
                )
            }
    }

    suspend fun addStudents(s: Session, names: List<Pair<String, String>>): AddResult = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        names.forEach { arr.put(JSONObject().put("firstName", it.first).put("lastName", it.second)) }
        val o = json(s.serverUrl, "/api/class/students", "POST", s.token, JSONObject().put("students", arr))
        val dups = o.optJSONArray("duplicates")
        AddResult(
            added = o.optInt("added"),
            duplicates = if (dups == null) emptyList() else List(dups.length()) { dups.getString(it) },
            invalid = o.optInt("invalid"),
        )
    }

    suspend fun deleteStudent(s: Session, id: String) = withContext(Dispatchers.IO) {
        raw(s.serverUrl, "/api/class/students/$id", "DELETE", s.token, null, null)
        Unit
    }

    suspend fun releaseStudent(s: Session, id: String) = withContext(Dispatchers.IO) {
        json(s.serverUrl, "/api/class/students/$id/release", "POST", s.token, JSONObject())
        Unit
    }

    suspend fun blockStudent(s: Session, id: String, minutes: Int) = withContext(Dispatchers.IO) {
        json(s.serverUrl, "/api/class/students/$id/block", "POST", s.token, JSONObject().put("minutes", minutes))
        Unit
    }

    // ---------- classes (super admin) ----------

    suspend fun adminClasses(s: Session): List<ClassInfo> = withContext(Dispatchers.IO) {
        json(s.serverUrl, "/api/admin/classes", "GET", s.token, null)
            .getJSONArray("classes")
            .mapObjects { ClassInfo(it.getString("id"), it.getString("name"), it.optInt("students")) }
    }

    suspend fun createClass(s: Session, name: String, password: String) = withContext(Dispatchers.IO) {
        json(
            s.serverUrl, "/api/admin/classes", "POST", s.token,
            JSONObject().put("name", name).put("password", password),
        )
        Unit
    }

    suspend fun updateClass(s: Session, id: String, name: String, password: String?) = withContext(Dispatchers.IO) {
        val b = JSONObject().put("name", name)
        if (!password.isNullOrBlank()) b.put("password", password)
        json(s.serverUrl, "/api/admin/classes/$id", "PUT", s.token, b)
        Unit
    }

    suspend fun deleteClass(s: Session, id: String) = withContext(Dispatchers.IO) {
        raw(s.serverUrl, "/api/admin/classes/$id", "DELETE", s.token, null, null)
        Unit
    }

    // ---------- messages ----------

    private fun durationText(seconds: Long): String {
        val min = ((seconds + 59) / 60).toInt().coerceAtLeast(1)
        return if (min >= 1440) "${(min / 1440).fa()} روز" else formatDuration(min)
    }

    fun message(e: ApiException): String = when {
        e.code == 0 -> "اتصال به سرور برقرار نشد. آدرس و اینترنت را بررسی کنید."
        e.code < 0 -> e.message ?: "خطا"
        else -> when (e.key) {
            "wrong_secret" -> "رمز وارد‌شده درست نیست."
            "not_in_list" -> "این نام در لیست این کلاس نیست. نام و نام خانوادگی را مثل لیست کلاس بنویسید، یا از مدیر کلاس بخواهید اضافه‌تان کند."
            "blocked" -> "مدیر کلاس دسترسی شما را مسدود کرده است. حدود ${durationText(e.seconds)} دیگر دوباره امتحان کنید."
            "device_locked" -> "این نام روی دستگاه دیگری فعال است."
            "bad_code" -> "کد تأیید درست نیست یا منقضی شده است."
            "too_many_attempts" -> "تلاش‌های ناموفق زیاد بود؛ چند دقیقه بعد دوباره امتحان کنید."
            "device_replaced" -> "این حساب روی دستگاه دیگری فعال شده است."
            "removed" -> "حساب یا کلاس شما دیگر وجود ندارد."
            "forbidden" -> "این کار برای شما مجاز نیست."
            "name_taken" -> "کلاسی با این نام از قبل وجود دارد."
            "class_not_found" -> "این کلاس پیدا نشد؛ لیست کلاس‌ها را دوباره بگیرید."
            "unauthorized" -> "نشست شما منقضی شده؛ دوباره وارد شوید."
            "too_large" -> "حجم عکس زیاد است."
            "invalid" -> "اطلاعات واردشده معتبر نیست (رمز کلاس باید حداقل ۴ نویسه باشد)."
            else -> "خطای سرور (${e.code})"
        }
    }

    /** خطاهایی که یعنی نشست دیگر معتبر نیست و باید به صفحهٔ ورود برگشت. */
    fun isSessionDead(e: ApiException): Boolean =
        e.code == 401 || (e.code == 403 && e.key == "blocked")
}
