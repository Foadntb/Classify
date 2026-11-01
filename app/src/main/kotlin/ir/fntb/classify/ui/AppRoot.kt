@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.fntb.classify.data.CLASS_REMINDER_MINUTES
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.data.Role
import ir.fntb.classify.data.Session
import ir.fntb.classify.data.ThemeMode
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.faDigits

private data class NavItem(
    val id: String,
    val label: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val s = session
    CompositionLocalProvider(LocalPhotoLoader provides vm.photoLoader) {
        when {
            s == null -> LoginScreen(vm)
            s.role == Role.SUPER -> SuperAdminScreen(vm)
            else -> MainContent(vm, s)
        }
    }
}

@Composable
private fun MainContent(vm: MainViewModel, session: Session) {
    val role = session.role
    val isAdmin = role == Role.MANAGER
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val pair by vm.pair.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val items = buildList {
        add(NavItem("today", "امروز", "امروز", Icons.Outlined.Home, Icons.Filled.Home))
        add(NavItem("schedule", "برنامه", "برنامه هفتگی", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth))
        add(NavItem("homework", "تکالیف", "تکالیف درسی", Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt))
        add(NavItem("stats", "آمار", "آمار و پیشرفت", Icons.Outlined.BarChart, Icons.Filled.BarChart))
        if (isAdmin) add(NavItem("students", "دانش‌آموزان", "دانش‌آموزان کلاس", Icons.Outlined.Groups, Icons.Filled.Groups))
    }
    val current = tab.coerceIn(0, items.lastIndex)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(items[current].title) },
                    actions = {
                        if (sync.busy) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { vm.refresh() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "به‌روزرسانی")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "تنظیمات")
                        }
                    },
                )
                sync.error?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (isAdmin && vm.hasUnsynced) "$it\nتغییرات شما ذخیره شده و بعد از اتصال ارسال می‌شود." else "$it\nداده‌های ذخیره‌شدهٔ قبلی نمایش داده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = current == i,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                if (current == i) item.selectedIcon else item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(item.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        when (items[current].id) {
            "today" -> TodayScreen(
                data = data,
                vm = vm,
                padding = padding,
                isAdmin = isAdmin,
                greeting = if (isAdmin) "سلام، مدیر کلاس 👋" else "سلام ${session.displayName} 👋",
            )
            "schedule" -> ScheduleScreen(data, vm, padding, isAdmin)
            "homework" -> HomeworkScreen(data, vm, padding, isAdmin)
            "stats" -> StatsScreen(data, padding)
            else -> StudentsScreen(vm, padding)
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("تنظیمات") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "یادآوری ${CLASS_REMINDER_MINUTES.fa()} دقیقه قبل از شروع کلاس‌ها",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = data.classReminder, onCheckedChange = { vm.setClassReminder(it) })
                    }
                    Text("ظاهر برنامه", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(
                                selected = themeMode == m,
                                onClick = { vm.setThemeMode(m) },
                                label = { Text(m.label) },
                            )
                        }
                    }
                    if (role == Role.STUDENT) {
                        HorizontalDivider()
                        Text(
                            "اگر می‌خواهید با گوشی یا دستگاه دیگری وارد شوید، اینجا کد تأیید بگیرید و در دستگاه جدید وارد کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showSettings = false; vm.requestPairCode() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("کد تأیید برای دستگاه جدید") }
                    }
                    HorizontalDivider()
                    Text("وارد شده‌اید به‌عنوان: ${role.label}", style = MaterialTheme.typography.bodyMedium)
                    if (session.className.isNotBlank()) {
                        Text("کلاس ${session.className}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("بستن") } },
            dismissButton = {
                TextButton(onClick = { showSettings = false; confirmLogout = true }) {
                    Text("خروج از حساب", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (pair.busy || pair.code != null || pair.error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearPairCode() },
            title = { Text("کد تأیید دستگاه جدید") },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val code = pair.code
                    when {
                        pair.busy -> CircularProgressIndicator()
                        code != null -> {
                            Text(
                                code.chunked(3).joinToString(" ").faDigits(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "این کد را در دستگاه جدید (در صفحهٔ ورود، با همین نام) وارد کنید. حدود ${(pair.seconds / 60).coerceAtLeast(1).fa()} دقیقه معتبر است. بعد از تأیید، این دستگاه از حساب خارج می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> Text(pair.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearPairCode() }) { Text("بستن") } },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "خروج از حساب؟",
            message = if (isAdmin && vm.hasUnsynced) {
                "تغییراتی هنوز به سرور ارسال نشده و با خروج از بین می‌روند."
            } else if (role == Role.STUDENT) {
                "برای دیدن دوباره‌ی برنامه باید دوباره وارد شوید. نام شما همچنان به همین دستگاه قفل می‌ماند."
            } else {
                "برای دیدن دوباره‌ی برنامه باید دوباره وارد شوید."
            },
            confirmLabel = "خروج",
            onConfirm = { confirmLogout = false; vm.logout() },
            onDismiss = { confirmLogout = false },
        )
    }
}
