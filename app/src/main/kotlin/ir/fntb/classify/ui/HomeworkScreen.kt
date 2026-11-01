@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.fntb.classify.data.AppData
import ir.fntb.classify.data.Course
import ir.fntb.classify.data.Homework
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.data.Priority
import ir.fntb.classify.data.ReminderType
import ir.fntb.classify.data.newId
import ir.fntb.classify.ui.theme.courseColor
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.fa
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class HwFilter(val label: String) {
    PENDING("در انتظار"),
    OVERDUE("عقب‌افتاده"),
    DONE("انجام‌شده"),
    ALL("همه"),
}

@Composable
fun HomeworkScreen(data: AppData, vm: MainViewModel, padding: PaddingValues, isAdmin: Boolean) {
    val ctx = LocalContext.current
    val today = remember { LocalDate.now() }
    var filterName by rememberSaveable { mutableStateOf(HwFilter.PENDING.name) }
    val filter = HwFilter.valueOf(filterName)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Homework?>(null) }
    var needCourse by remember { mutableStateOf(false) }

    val courseById = remember(data.courses) { data.courses.associateBy { it.id } }

    fun matches(h: Homework, f: HwFilter) = when (f) {
        HwFilter.PENDING -> !h.done
        HwFilter.OVERDUE -> !h.done && h.due.isBefore(today)
        HwFilter.DONE -> h.done
        HwFilter.ALL -> true
    }

    val list = data.homework
        .filter { matches(it, filter) }
        .sortedWith(compareBy<Homework> { it.done }.thenBy { it.due })

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(HwFilter.entries.toList()) { f ->
                    val n = data.homework.count { matches(it, f) }
                    FilterChip(
                        selected = f == filter,
                        onClick = { filterName = f.name },
                        label = { Text("${f.label} (${n.fa()})") },
                    )
                }
            }

            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    text = when {
                        data.homework.isEmpty() -> if (isAdmin) "هنوز تکلیفی ثبت نشده است. با دکمهٔ «تکلیف جدید» شروع کنید." else "هنوز تکلیفی از طرف مدیر ثبت نشده است."
                        filter == HwFilter.OVERDUE -> "تکلیف عقب‌افتاده‌ای وجود ندارد. آفرین!"
                        filter == HwFilter.DONE -> "هنوز تکلیفی انجام‌شده علامت نخورده است."
                        else -> "تکلیفی در این دسته نیست."
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { hw ->
                        HomeworkCard(
                            hw = hw,
                            course = courseById[hw.courseId],
                            today = today,
                            onToggle = { vm.toggleHomework(hw.id) },
                            onEdit = if (isAdmin) ({ editing = hw }) else null,
                            onShare = if (isAdmin) ({ shareHomework(ctx, hw, courseById[hw.courseId]) }) else null,
                        )
                    }
                }
            }
        }

        if (isAdmin) ExtendedFloatingActionButton(
            onClick = { if (data.courses.isEmpty()) needCourse = true else adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("تکلیف جدید") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (needCourse) {
        AlertDialog(
            onDismissRequest = { needCourse = false },
            title = { Text("اول یک درس بسازید") },
            text = { Text("برای ثبت تکلیف باید حداقل یک درس داشته باشید. از تب «برنامه» روی «درس‌ها» بزنید.") },
            confirmButton = { TextButton(onClick = { needCourse = false }) { Text("باشه") } },
        )
    }
    if (adding) {
        HomeworkDialog(
            initial = null,
            courses = data.courses,
            onDismiss = { adding = false },
            onSave = { vm.saveHomework(it); adding = false },
            onDelete = null,
        )
    }
    editing?.let { hw ->
        HomeworkDialog(
            initial = hw,
            courses = data.courses,
            onDismiss = { editing = null },
            onSave = { vm.saveHomework(it); editing = null },
            onDelete = { vm.deleteHomework(hw.id); editing = null },
        )
    }
}

private fun dueText(due: LocalDate, today: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(today, due).toInt()
    return when {
        diff < 0 -> "${(-diff).fa()} روز از مهلت گذشته"
        diff == 0 -> "مهلت: امروز"
        diff == 1 -> "مهلت: فردا"
        else -> "${diff.fa()} روز تا مهلت"
    }
}

@Composable
private fun HomeworkCard(
    hw: Homework,
    course: Course?,
    today: LocalDate,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)?,
    onShare: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val overdue = !hw.done && hw.due.isBefore(today)
    val soon = !hw.done && !overdue && ChronoUnit.DAYS.between(today, hw.due) <= 1
    val dueColor = when {
        hw.done -> scheme.onSurfaceVariant
        overdue -> scheme.error
        soon -> scheme.tertiary
        else -> scheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.clickable(enabled = onEdit != null) { onEdit?.invoke() }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = hw.done, onCheckedChange = { onToggle() })
            Column(
                Modifier.weight(1f).padding(top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    hw.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (hw.done) TextDecoration.LineThrough else null,
                    color = if (hw.done) scheme.onSurfaceVariant else scheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CourseAvatar(course, 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(course?.name ?: "درس حذف‌شده", style = MaterialTheme.typography.labelLarge)
                    if (hw.priority == Priority.HIGH && !hw.done) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "اولویت زیاد",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.error,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DaysLeftChip(hw.due, today, hw.done)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        JalaliCalendar.format(hw.due),
                        style = MaterialTheme.typography.bodySmall,
                        color = dueColor,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (hw.details.isNotBlank()) {
                    Text(
                        hw.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onShare != null) IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "ارسال برای دانش‌آموزان")
            }
        }
    }
}

private fun shareHomework(ctx: Context, hw: Homework, course: Course?) {
    val text = buildString {
        appendLine("📚 تکلیف ${course?.name.orEmpty()}".trim())
        appendLine("عنوان: ${hw.title}")
        appendLine("مهلت تحویل: ${JalaliCalendar.formatWithWeekday(hw.due)}")
        if (hw.details.isNotBlank()) appendLine("توضیحات: ${hw.details}")
    }.trim()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "ارسال تکلیف"))
}

@Composable
fun HomeworkDialog(
    initial: Homework?,
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (Homework) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var details by remember { mutableStateOf(initial?.details ?: "") }
    var course by remember {
        mutableStateOf(courses.firstOrNull { it.id == initial?.courseId } ?: courses.firstOrNull())
    }
    var due by remember { mutableStateOf(initial?.due ?: LocalDate.now().plusDays(1)) }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.NORMAL) }
    var reminder by remember { mutableStateOf(initial?.reminder ?: ReminderType.DAY_BEFORE) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تکلیف جدید" else "ویرایش تکلیف") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان تکلیف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("توضیحات (اختیاری)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField("درس", courses, course, { it.name }) { course = it }
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مهلت: ${JalaliCalendar.formatWithWeekday(due)}")
                }
                Text("اولویت", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = priority == p,
                            onClick = { priority = p },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Priority.entries.size),
                        ) { Text(p.label) }
                    }
                }
                DropdownField("یادآوری", ReminderType.entries.toList(), reminder, { it.label }) { reminder = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && course != null,
                onClick = {
                    val c = course ?: return@TextButton
                    onSave(
                        Homework(
                            id = initial?.id ?: newId(),
                            courseId = c.id,
                            title = title.trim(),
                            details = details.trim(),
                            due = due,
                            priority = priority,
                            reminder = reminder,
                            done = initial?.done ?: false,
                        )
                    )
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

    if (pickDate) {
        JalaliDatePickerDialog(
            initial = due,
            onDismiss = { pickDate = false },
            onConfirm = { due = it; pickDate = false },
        )
    }
}
