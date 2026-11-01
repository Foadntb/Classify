@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import ir.fntb.classify.util.PhotoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ir.fntb.classify.data.Course
import ir.fntb.classify.data.newId
import ir.fntb.classify.ui.theme.CoursePalette
import ir.fntb.classify.ui.theme.courseColor
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.JalaliDate
import ir.fntb.classify.util.fa
import java.time.LocalDate

// ------------------------------------------------------------------
// انتخاب ساعت
// ------------------------------------------------------------------

@Composable
fun TimeDialog(
    title: String,
    initialMin: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("تأیید") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// تقویم شمسی
// ------------------------------------------------------------------

@Composable
fun JalaliDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val init = remember { JalaliCalendar.fromLocalDate(initial) }
    val today = remember { JalaliCalendar.fromLocalDate(LocalDate.now()) }
    var year by remember { mutableIntStateOf(init.year) }
    var month by remember { mutableIntStateOf(init.month) }
    var selected by remember { mutableStateOf(init) }

    val offset = JalaliCalendar.firstWeekdayIndex(year, month)
    val daysInMonth = JalaliCalendar.daysInMonth(year, month)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(JalaliCalendar.format(selected)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                    }) { Icon(Icons.Filled.ChevronRight, contentDescription = "ماه قبل") }
                    Text(
                        "${JalaliCalendar.monthNames[month - 1]} ${year.fa()}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                    }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "ماه بعد") }
                }
                Row(Modifier.fillMaxWidth()) {
                    JalaliCalendar.weekDaysShort.forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val cells = List(offset) { 0 } + (1..daysInMonth).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i) ?: 0
                            DayCell(
                                day = d,
                                isSelected = d != 0 && selected == JalaliDate(year, month, d),
                                isToday = d != 0 && today == JalaliDate(year, month, d),
                                onClick = { selected = JalaliDate(year, month, d) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(JalaliCalendar.toLocalDate(selected.year, selected.month, selected.day))
            }) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun RowScope.DayCell(day: Int, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var m = Modifier
        .weight(1f)
        .aspectRatio(1f)
        .padding(2.dp)
        .clip(CircleShape)
    if (isSelected) m = m.background(scheme.primary)
    else if (isToday) m = m.border(1.dp, scheme.primary, CircleShape)
    if (day != 0) m = m.clickable(onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) {
        if (day != 0) {
            Text(
                day.fa(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) scheme.onPrimary else scheme.onSurface,
            )
        }
    }
}

// ------------------------------------------------------------------
// درس‌ها
// ------------------------------------------------------------------

@Composable
fun CoursesSheet(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (Course, ByteArray?) -> Unit,
    onDelete: (Course) -> Unit,
) {
    var editing by remember { mutableStateOf<Course?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Course?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("درس‌ها", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("درس جدید")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (courses.isEmpty()) {
                Text(
                    "هنوز درسی ثبت نشده است. با «درس جدید» شروع کنید.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(courses, key = { it.id }) { c ->
                        ListItem(
                            modifier = Modifier.clickable { editing = c },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { CourseAvatar(c, 40.dp) },
                            headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                            supportingContent = if (c.teacher.isNotBlank()) {
                                { Text(c.teacher) }
                            } else null,
                            trailingContent = {
                                IconButton(onClick = { deleting = c }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "حذف درس")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        CourseDialog(
            initial = null,
            defaultColor = courses.size,
            onDismiss = { adding = false },
            onSave = { c, p -> onSave(c, p); adding = false },
        )
    }
    editing?.let { c ->
        CourseDialog(
            initial = c,
            defaultColor = c.colorIndex,
            onDismiss = { editing = null },
            onSave = { c, p -> onSave(c, p); editing = null },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "حذف «${c.name}»؟",
            message = "کلاس‌ها و تکالیف مربوط به این درس هم حذف می‌شوند.",
            confirmLabel = "حذف",
            onConfirm = { onDelete(c); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun CourseDialog(
    initial: Course?,
    defaultColor: Int,
    onDismiss: () -> Unit,
    onSave: (Course, ByteArray?) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var color by remember { mutableIntStateOf(defaultColor.mod(CoursePalette.size)) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            photoError = false
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { PhotoUtil.prepare(ctx, uri) }
                if (bytes != null) {
                    photo = bytes
                    preview = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                } else {
                    photoError = true
                }
                busy = false
            }
        }
    }

    val avatarCourse = (initial ?: Course("new", name, "", color)).copy(
        name = name.ifBlank { "؟" },
        colorIndex = color,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "درس جدید" else "ویرایش درس") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape)) {
                        CourseAvatar(avatarCourse, 64.dp)
                        val pv = preview
                        if (pv != null) {
                            Image(
                                bitmap = pv,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (preview != null || (initial?.photoVer ?: 0) > 0) "تغییر عکس" else "افزودن عکس")
                    }
                }
                if (busy) Text("در حال آماده‌سازی عکس…", style = MaterialTheme.typography.bodySmall)
                if (photoError) {
                    Text(
                        "این عکس باز نشد؛ عکس دیگری انتخاب کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام درس") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("نام معلم (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("رنگ درس", style = MaterialTheme.typography.labelLarge)
                CoursePalette.indices.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { i ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(courseColor(i))
                                    .clickable { color = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (color == i) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    onSave(
                        Course(initial?.id ?: newId(), name.trim(), teacher.trim(), color, initial?.photoVer ?: 0),
                        photo,
                    )
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
