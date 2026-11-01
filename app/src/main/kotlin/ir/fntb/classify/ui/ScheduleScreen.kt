@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.fntb.classify.data.AppData
import ir.fntb.classify.data.ClassSlot
import ir.fntb.classify.data.Course
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.data.newId
import ir.fntb.classify.ui.theme.courseColor
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.formatTime
import ir.fntb.classify.util.weekdayIndex
import java.time.LocalDate

@Composable
fun ScheduleScreen(data: AppData, vm: MainViewModel, padding: PaddingValues, isAdmin: Boolean) {
    val today = remember { LocalDate.now() }
    var selectedDay by rememberSaveable { mutableIntStateOf(today.weekdayIndex()) }
    var showCourses by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClassSlot?>(null) }

    val courseById = remember(data.courses) { data.courses.associateBy { it.id } }
    val slots = data.slots.filter { it.day == selectedDay }.sortedBy { it.startMin }

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "امروز",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(JalaliCalendar.formatWithWeekday(today), style = MaterialTheme.typography.titleMedium)
                }
                if (isAdmin) FilledTonalButton(onClick = { showCourses = true }) {
                    Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("درس‌ها")
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((0..6).toList()) { d ->
                    val n = data.slots.count { it.day == d }
                    FilterChip(
                        selected = d == selectedDay,
                        onClick = { selectedDay = d },
                        label = { Text(JalaliCalendar.weekDays[d] + if (n > 0) " (${n.fa()})" else "") },
                        leadingIcon = if (d == today.weekdayIndex()) {
                            { Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }

            if (slots.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    text = if (!isAdmin && data.courses.isEmpty()) {
                        "هنوز برنامه‌ای توسط مدیر ثبت نشده است."
                    } else if (data.courses.isEmpty()) {
                        "ابتدا از دکمهٔ «درس‌ها» چند درس اضافه کنید، بعد کلاس‌ها را در برنامه بگذارید."
                    } else {
                        "برای ${JalaliCalendar.weekDays[selectedDay]} کلاسی ثبت نشده است."
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(slots, key = { it.id }) { slot ->
                        SlotCard(slot, courseById[slot.courseId], if (isAdmin) ({ editing = slot }) else null)
                    }
                }
            }
        }

        if (isAdmin) ExtendedFloatingActionButton(
            onClick = { if (data.courses.isEmpty()) showCourses = true else adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("کلاس جدید") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showCourses) {
        CoursesSheet(
            courses = data.courses,
            onDismiss = { showCourses = false },
            onSave = { c, p -> vm.saveCourse(c, p) },
            onDelete = { vm.deleteCourse(it.id) },
        )
    }
    if (adding) {
        SlotDialog(
            initial = null,
            courses = data.courses,
            defaultDay = selectedDay,
            onDismiss = { adding = false },
            onSave = { vm.saveSlot(it); adding = false },
            onDelete = null,
        )
    }
    editing?.let { slot ->
        SlotDialog(
            initial = slot,
            courses = data.courses,
            defaultDay = slot.day,
            onDismiss = { editing = null },
            onSave = { vm.saveSlot(it); editing = null },
            onDelete = { vm.deleteSlot(slot.id); editing = null },
        )
    }
}

@Composable
private fun SlotCard(slot: ClassSlot, course: Course?, onClick: (() -> Unit)?) {
    val color = courseColor(course?.colorIndex ?: 0)
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).clickable(enabled = onClick != null) { onClick?.invoke() }) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            CourseAvatar(course, 44.dp, Modifier.align(Alignment.CenterVertically).padding(start = 12.dp))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    course?.name ?: "درس حذف‌شده",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                InfoLine(Icons.Outlined.Schedule, "${formatTime(slot.startMin)} تا ${formatTime(slot.endMin)}")
                if (slot.room.isNotBlank()) InfoLine(Icons.Outlined.Place, slot.room)
                if (!course?.teacher.isNullOrBlank()) InfoLine(Icons.Outlined.Person, course!!.teacher)
            }
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SlotDialog(
    initial: ClassSlot?,
    courses: List<Course>,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (ClassSlot) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var course by remember { mutableStateOf(courses.firstOrNull { it.id == initial?.courseId } ?: courses.firstOrNull()) }
    var day by remember { mutableIntStateOf(initial?.day ?: defaultDay) }
    var start by remember { mutableIntStateOf(initial?.startMin ?: (8 * 60)) }
    var end by remember { mutableIntStateOf(initial?.endMin ?: (8 * 60 + 45)) }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var pickTime by remember { mutableStateOf<Int?>(null) } // 0 = شروع، 1 = پایان

    val valid = course != null && end > start

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "کلاس جدید" else "ویرایش کلاس") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DropdownField("درس", courses, course, { it.name }) { course = it }
                DropdownField("روز هفته", (0..6).toList(), day, { JalaliCalendar.weekDays[it] }) { day = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime = 0 }, modifier = Modifier.weight(1f)) {
                        Text("شروع ${formatTime(start)}")
                    }
                    OutlinedButton(onClick = { pickTime = 1 }, modifier = Modifier.weight(1f)) {
                        Text("پایان ${formatTime(end)}")
                    }
                }
                if (end <= start) {
                    Text(
                        "ساعت پایان باید بعد از شروع باشد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("کلاس / اتاق (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val c = course ?: return@TextButton
                    onSave(ClassSlot(initial?.id ?: newId(), c.id, day, start, end, room.trim()))
                },
            ) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )

    pickTime?.let { which ->
        TimeDialog(
            title = if (which == 0) "ساعت شروع کلاس" else "ساعت پایان کلاس",
            initialMin = if (which == 0) start else end,
            onDismiss = { pickTime = null },
            onConfirm = { m ->
                if (which == 0) {
                    start = m
                    if (end <= start) end = minOf(start + 45, 24 * 60 - 1)
                } else {
                    end = m
                }
                pickTime = null
            },
        )
    }
}
