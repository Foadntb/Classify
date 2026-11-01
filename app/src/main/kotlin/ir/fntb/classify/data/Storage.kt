package ir.fntb.classify.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

object Storage {
    private const val FILE = "data.json"

    @Synchronized
    fun load(ctx: Context): AppData {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return AppData()
        return try {
            parse(JSONObject(f.readText()))
        } catch (e: Exception) {
            AppData()
        }
    }

    @Synchronized
    fun save(ctx: Context, data: AppData) {
        val target = File(ctx.filesDir, FILE)
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(toJson(data).toString())
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        List(length()) { f(getJSONObject(it)) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    fun parse(o: JSONObject): AppData = AppData(
        courses = o.optJSONArray("courses")?.mapObjects {
            Course(
                id = it.getString("id"),
                name = it.getString("name"),
                teacher = it.optString("teacher"),
                colorIndex = it.optInt("color"),
                photoVer = it.optInt("photoVer"),
            )
        } ?: emptyList(),
        slots = o.optJSONArray("slots")?.mapObjects {
            ClassSlot(
                id = it.getString("id"),
                courseId = it.getString("courseId"),
                day = it.getInt("day"),
                startMin = it.getInt("start"),
                endMin = it.getInt("end"),
                room = it.optString("room"),
            )
        } ?: emptyList(),
        homework = o.optJSONArray("homework")?.mapObjects {
            Homework(
                id = it.getString("id"),
                courseId = it.getString("courseId"),
                title = it.getString("title"),
                details = it.optString("details"),
                due = LocalDate.parse(it.getString("due")),
                priority = enumOr(it.optString("priority"), Priority.NORMAL),
                reminder = enumOr(it.optString("reminder"), ReminderType.NONE),
                done = it.optBoolean("done"),
            )
        } ?: emptyList(),
        announcements = o.optJSONArray("announcements")?.mapObjects {
            Announcement(
                id = it.getString("id"),
                text = it.getString("text"),
                createdAt = it.optLong("createdAt"),
            )
        } ?: emptyList(),
        classReminder = o.optBoolean("classReminder", true),
        className = o.optString("className"),
    )

    fun toJson(d: AppData): JSONObject = JSONObject().apply {
        put("classReminder", d.classReminder)
        put("className", d.className)
        put("announcements", JSONArray().also { arr ->
            d.announcements.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("text", it.text)
                        .put("createdAt", it.createdAt)
                )
            }
        })
        put("courses", JSONArray().also { arr ->
            d.courses.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("teacher", it.teacher)
                        .put("color", it.colorIndex)
                        .put("photoVer", it.photoVer)
                )
            }
        })
        put("slots", JSONArray().also { arr ->
            d.slots.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("courseId", it.courseId)
                        .put("day", it.day)
                        .put("start", it.startMin)
                        .put("end", it.endMin)
                        .put("room", it.room)
                )
            }
        })
        put("homework", JSONArray().also { arr ->
            d.homework.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("courseId", it.courseId)
                        .put("title", it.title)
                        .put("details", it.details)
                        .put("due", it.due.toString())
                        .put("priority", it.priority.name)
                        .put("reminder", it.reminder.name)
                        .put("done", it.done)
                )
            }
        })
    }
}
