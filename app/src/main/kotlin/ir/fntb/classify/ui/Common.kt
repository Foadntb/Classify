@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import ir.fntb.classify.data.Course
import ir.fntb.classify.ui.theme.courseColor
import ir.fntb.classify.util.fa
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 12.dp) {
    Spacer(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let(text) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

// ------------------------------------------------------------------
// عکس / آواتار درس
// ------------------------------------------------------------------

private val NoPhotos: suspend (String, Int) -> ImageBitmap? = { _, _ -> null }

/** تابعی که عکس درس را (از کش یا سرور) می‌آورد؛ در AppRoot مقدار می‌گیرد. */
val LocalPhotoLoader = staticCompositionLocalOf { NoPhotos }

/** اگر درس عکس داشته باشد عکس، وگرنه دایره‌ای به رنگ درس با اولین حرف نام. */
@Composable
fun CourseAvatar(course: Course?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val color = courseColor(course?.colorIndex ?: 0)
    val loader = LocalPhotoLoader.current
    val id = course?.id
    val ver = course?.photoVer ?: 0
    var bmp by remember(id, ver) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id, ver) {
        bmp = if (id != null && ver > 0) loader(id, ver) else null
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                (course?.name?.trim()?.firstOrNull() ?: '؟').toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// ------------------------------------------------------------------
// شمارش روزهای مانده تا مهلت
// ------------------------------------------------------------------

@Composable
fun DaysLeftChip(due: LocalDate, today: LocalDate, done: Boolean) {
    val diff = ChronoUnit.DAYS.between(today, due).toInt()
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        done -> scheme.surfaceVariant to scheme.onSurfaceVariant
        diff < 0 -> scheme.errorContainer to scheme.onErrorContainer
        diff <= 1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    val label = when {
        done -> "انجام شد"
        diff < 0 -> "${(-diff).fa()} روز گذشته"
        diff == 0 -> "امروز"
        diff == 1 -> "فردا"
        else -> "${diff.fa()} روز مانده"
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
