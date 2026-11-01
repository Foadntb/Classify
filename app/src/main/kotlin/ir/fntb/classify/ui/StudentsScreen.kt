@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.data.StudentInfo
import ir.fntb.classify.util.enDigits
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.formatDuration

/** لیست دانش‌آموزان کلاس (فقط مدیر کلاس): افزودن، حذف، آزاد کردن قفل دستگاه و مسدود کردن. */
@Composable
fun StudentsScreen(vm: MainViewModel, padding: PaddingValues) {
    val state by vm.students.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf<StudentInfo?>(null) }
    var deleting by remember { mutableStateOf<StudentInfo?>(null) }

    LaunchedEffect(Unit) { vm.loadStudents() }

    val nowSec = System.currentTimeMillis() / 1000

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "${state.list.size.fa()} دانش‌آموز",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.list.isEmpty() && !state.busy) {
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    text = "هنوز دانش‌آموزی ثبت نشده است. با دکمهٔ «افزودن» اسم بچه‌های کلاس را وارد کنید؛ فقط همین افراد می‌توانند وارد کلاس شوند.",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.list, key = { it.id }) { st ->
                        StudentRow(
                            st = st,
                            nowSec = nowSec,
                            onRelease = { vm.releaseStudent(st.id) },
                            onBlock = { blocking = st },
                            onUnblock = { vm.blockStudent(st.id, 0) },
                            onDelete = { deleting = st },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("افزودن") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        AddStudentsDialog(
            onDismiss = { adding = false },
            onAdd = { vm.addStudents(it); adding = false },
        )
    }
    blocking?.let { st ->
        BlockDialog(
            name = st.fullName,
            onDismiss = { blocking = null },
            onBlock = { minutes -> vm.blockStudent(st.id, minutes); blocking = null },
        )
    }
    deleting?.let { st ->
        ConfirmDialog(
            title = "حذف «${st.fullName}»؟",
            message = "این دانش‌آموز از لیست کلاس حذف می‌شود و دیگر نمی‌تواند وارد شود. اگر فقط می‌خواهید موقتاً دسترسی‌اش قطع شود، از «مسدود کردن» استفاده کنید.",
            confirmLabel = "حذف",
            onConfirm = { vm.deleteStudent(st.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun StudentRow(
    st: StudentInfo,
    nowSec: Long,
    onRelease: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val blocked = st.blockedUntil > nowSec
    val status = when {
        blocked -> "مسدود؛ حدود ${blockDurationText(st.blockedUntil - nowSec)} دیگر"
        st.deviceLocked -> "وارد شده (دستگاه قفل است)"
        else -> "هنوز وارد نشده"
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (blocked) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st.firstName.firstOrNull()?.toString() ?: "؟",
                    fontWeight = FontWeight.Bold,
                    color = if (blocked) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        headlineContent = { Text(st.fullName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                status,
                color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "گزینه‌ها")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (st.deviceLocked) {
                        DropdownMenuItem(text = { Text("آزاد کردن دستگاه") }, onClick = { menu = false; onRelease() })
                    }
                    if (blocked) {
                        DropdownMenuItem(text = { Text("رفع مسدودیت") }, onClick = { menu = false; onUnblock() })
                    } else {
                        DropdownMenuItem(text = { Text("مسدود کردن…") }, onClick = { menu = false; onBlock() })
                    }
                    DropdownMenuItem(
                        text = { Text("حذف از کلاس", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
    )
}

private fun blockDurationText(seconds: Long): String {
    val min = ((seconds + 59) / 60).toInt().coerceAtLeast(1)
    return if (min >= 1440) "${(min / 1440).fa()} روز" else formatDuration(min)
}

@Composable
private fun AddStudentsDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن دانش‌آموزان") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "هر خط یک دانش‌آموز، به شکل «نام نام‌خانوادگی». می‌توانید لیست کلاس را یکجا پیست کنید.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("لیست دانش‌آموزان") },
                    placeholder = { Text("علی رضایی\nسارا احمدی") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "اگر نام دو بخشی است (مثل «محمد حسین»)، با «،» جدا کنید: محمد حسین، احمدی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text) }) { Text("افزودن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun BlockDialog(name: String, onDismiss: () -> Unit, onBlock: (Int) -> Unit) {
    var amount by remember { mutableStateOf("10") }
    // 0 = دقیقه، 1 = ساعت، 2 = روز
    var unit by remember { mutableIntStateOf(0) }
    val factor = intArrayOf(1, 60, 1440)[unit]
    val n = amount.enDigits().toIntOrNull()
    val minutes = if (n != null && n > 0) minOf(n.toLong() * factor, 525_600L).toInt() else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مسدود کردن «$name»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "در این مدت دانش‌آموز نمی‌تواند وارد برنامه شود.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("مدت") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("دقیقه", "ساعت", "روز").forEachIndexed { i, label ->
                        FilterChip(selected = unit == i, onClick = { unit = i }, label = { Text(label) })
                    }
                }
                if (minutes > 0) {
                    Text(
                        "مسدود برای: ${if (minutes >= 1440) "${(minutes / 1440).fa()} روز" else formatDuration(minutes)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = minutes > 0, onClick = { onBlock(minutes) }) {
                Text("مسدود کن", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
