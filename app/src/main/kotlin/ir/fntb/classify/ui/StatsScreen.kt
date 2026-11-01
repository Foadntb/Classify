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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import ir.fntb.classify.data.AppData
import ir.fntb.classify.ui.theme.courseColor
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.formatDuration
import ir.fntb.classify.util.weekdayIndex
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StatsScreen(data: AppData, padding: PaddingValues) {
    val today = remember { LocalDate.now() }
    val hw = data.homework
    val total = hw.size
    val done = hw.count { it.done }
    val overdue = hw.count { !it.done && it.due.isBefore(today) }
    val pending = total - done
    val progress = if (total == 0) 0f else done.toFloat() / total

    if (hw.isEmpty() && data.slots.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                icon = Icons.Outlined.BarChart,
                text = "وقتی کلاس و تکلیف ثبت کنید، آمار و پیشرفت اینجا نمایش داده می‌شود.",
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OverviewCard(progress, total, done, pending, overdue)
        }
        item {
            WeekLoadCard(data, today)
        }
        if (data.courses.isNotEmpty() && hw.isNotEmpty()) {
            item { CourseProgressCard(data) }
        }
        if (data.slots.isNotEmpty()) {
            item { ClassLoadCard(data) }
        }
    }
}

@Composable
private fun OverviewCard(progress: Float, total: Int, done: Int, pending: Int, overdue: Int) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(104.dp),
                    strokeWidth = 10.dp,
                    color = scheme.primary,
                    trackColor = scheme.onPrimaryContainer.copy(alpha = 0.15f),
                )
                Text(
                    "${(progress * 100).roundToInt().fa()}٪",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "پیشرفت تکالیف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                StatLine("کل تکالیف", total, scheme.onPrimaryContainer)
                StatLine("انجام‌شده", done, scheme.primary)
                StatLine("در انتظار", pending, scheme.tertiary)
                StatLine("عقب‌افتاده", overdue, scheme.error)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(dot, 10.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.fa(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun WeekLoadCard(data: AppData, today: LocalDate) {
    val scheme = MaterialTheme.colorScheme
    val counts = (0..6).map { off ->
        data.homework.count { !it.done && it.due == today.plusDays(off.toLong()) }
    }
    val maxCount = max(counts.max(), 1)

    Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp)) {
            Text("تکالیف ۷ روز آینده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "تعداد تکلیف‌های انجام‌نشده به‌ازای هر روز مهلت",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(130.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                counts.forEachIndexed { i, c ->
                    val date = today.plusDays(i.toLong())
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(c.fa(), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(22.dp)
                                .height((80f * c / maxCount).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (c == 0) scheme.outlineVariant else scheme.primary)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (i == 0) "امروز" else JalaliCalendar.weekDaysShort[date.weekdayIndex()],
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseProgressCard(data: AppData) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("پیشرفت هر درس", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            data.courses.forEach { c ->
                val all = data.homework.count { it.courseId == c.id }
                val d = data.homework.count { it.courseId == c.id && it.done }
                val color = courseColor(c.colorIndex)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(color, 10.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (all == 0) "بدون تکلیف" else "${d.fa()} از ${all.fa()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (all == 0) 0f else d.toFloat() / all },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = color,
                        trackColor = color.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassLoadCard(data: AppData) {
    val scheme = MaterialTheme.colorScheme
    val totalMin = data.slots.sumOf { it.endMin - it.startMin }
    val perDay = (0..6).map { d -> data.slots.filter { it.day == d }.sumOf { it.endMin - it.startMin } }
    val busiest = perDay.withIndex().maxByOrNull { it.value }

    Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("بار کلاسی هفته", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            InfoRow("تعداد جلسه در هفته", data.slots.size.fa())
            InfoRow("مجموع زمان کلاس", formatDuration(totalMin))
            if (busiest != null && busiest.value > 0) {
                InfoRow("پرکارترین روز", "${JalaliCalendar.weekDays[busiest.index]} (${formatDuration(busiest.value)})")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
