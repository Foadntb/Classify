@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.fntb.classify.data.AppData
import ir.fntb.classify.data.Announcement
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.formatTime
import ir.fntb.classify.util.weekdayIndex
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** صفحهٔ «امروز»: اعلان‌های کلاس، کلاس‌های امروز و تکالیفی که مهلتشان نزدیک است. */
@Composable
fun TodayScreen(
    data: AppData,
    vm: MainViewModel,
    padding: PaddingValues,
    isAdmin: Boolean,
    greeting: String,
) {
    val today = LocalDate.now()
    val nowTime = LocalTime.now()
    val nowMin = nowTime.hour * 60 + nowTime.minute
    var addingAnnouncement by remember { mutableStateOf(false) }

    val courseById = remember(data.courses) { data.courses.associateBy { it.id } }
    val todaySlots = data.slots.filter { it.day == today.weekdayIndex() }.sortedBy { it.startMin }
    val nextSlotId = todaySlots.firstOrNull { it.startMin > nowMin }?.id
    val dueSoon = data.homework
        .filter { !it.done && !it.due.isAfter(today.plusDays(3)) }
        .sortedBy { it.due }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        JalaliCalendar.formatWithWeekday(today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (data.className.isNotBlank()) {
                        Text(
                            "کلاس ${data.className}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        if (data.announcements.isNotEmpty() || isAdmin) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("اعلان‌های کلاس", Modifier.weight(1f))
                    if (isAdmin) FilledTonalButton(onClick = { addingAnnouncement = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("اعلان جدید")
                    }
                }
            }
            if (data.announcements.isEmpty()) {
                item {
                    Text(
                        "هنوز اعلانی ثبت نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(data.announcements.take(5), key = { it.id }) { a ->
                AnnouncementCard(a, if (isAdmin) ({ vm.deleteAnnouncement(a.id) }) else null)
            }
        }

        item { SectionTitle("کلاس‌های امروز") }
        if (todaySlots.isEmpty()) {
            item {
                Text(
                    if (data.courses.isEmpty()) "هنوز برنامه‌ای ثبت نشده است." else "امروز کلاسی ندارید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(todaySlots, key = { "slot_" + it.id }) { slot ->
            val course = courseById[slot.courseId]
            val ongoing = nowMin >= slot.startMin && nowMin < slot.endMin
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CourseAvatar(course, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            course?.name ?: "درس حذف‌شده",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val room = if (slot.room.isNotBlank()) " · ${slot.room}" else ""
                        Text(
                            "${formatTime(slot.startMin)} تا ${formatTime(slot.endMin)}$room",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        ongoing -> Text(
                            "در حال برگزاری",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        slot.id == nextSlotId -> Text(
                            "کلاس بعدی",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        item { SectionTitle("تکالیف نزدیک") }
        if (dueSoon.isEmpty()) {
            item {
                Text(
                    "تکلیف نزدیکی ندارید. 🎉",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(dueSoon, key = { "hw_" + it.id }) { hw ->
            val course = courseById[hw.courseId]
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CourseAvatar(course, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            hw.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            course?.name ?: "درس حذف‌شده",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    DaysLeftChip(hw.due, today, hw.done)
                }
            }
        }
    }

    if (addingAnnouncement) {
        AnnouncementDialog(
            onDismiss = { addingAnnouncement = false },
            onSend = { vm.addAnnouncement(it); addingAnnouncement = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
private fun AnnouncementCard(a: Announcement, onDelete: (() -> Unit)?) {
    val date = Instant.ofEpochMilli(a.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.text, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    JalaliCalendar.format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "حذف اعلان",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اعلان جدید") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                label = { Text("متن اعلان") },
                minLines = 3,
                maxLines = 6,
                supportingText = { Text("${text.length.fa()} / ${500.fa()}") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSend(text) }) { Text("ارسال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
