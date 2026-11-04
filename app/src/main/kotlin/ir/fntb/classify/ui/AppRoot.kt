@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import ir.fntb.classify.BuildConfig
import ir.fntb.classify.data.BackupParts
import ir.fntb.classify.data.BackupPreview
import ir.fntb.classify.data.CLASS_REMINDER_MINUTES
import ir.fntb.classify.data.ChangelogEntry
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.data.Role
import ir.fntb.classify.data.Session
import ir.fntb.classify.data.ThemeMode
import ir.fntb.classify.data.UpdatePhase
import ir.fntb.classify.data.UpdateUi
import ir.fntb.classify.util.JalaliCalendar
import ir.fntb.classify.util.fa
import ir.fntb.classify.util.faDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val update by vm.update.collectAsStateWithLifecycle()
    val whatsNew by vm.whatsNew.collectAsStateWithLifecycle()
    val s = session

    // هر بار که اپ باز می‌شود: بررسی نسخهٔ جدید (حداکثر هر ۶ ساعت) و ادامهٔ نصب بعد از دادن اجازه
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onAppResumed() }

    CompositionLocalProvider(LocalPhotoLoader provides vm.photoLoader) {
        when {
            s == null -> LoginScreen(vm)
            s.role == Role.SUPER -> SuperAdminScreen(vm)
            else -> MainContent(vm, s)
        }
        // اول تازه‌های نسخهٔ تازه‌نصب‌شده؛ آپدیت بعدی بعد از آن
        if (whatsNew.isNotEmpty()) {
            WhatsNewDialog(whatsNew, onDismiss = { vm.dismissWhatsNew() })
        } else if (update.dialog && update.phase != UpdatePhase.IDLE && update.phase != UpdatePhase.CHECKING) {
            UpdateDialog(
                state = update,
                onDownload = { vm.startUpdateDownload() },
                onInstall = { vm.installUpdate() },
                onCancel = { vm.cancelUpdateDownload() },
                onLater = { vm.dismissUpdateDialog() },
            )
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
    val update by vm.update.collectAsStateWithLifecycle()
    val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
    val backupPreview by vm.backupPreview.collectAsStateWithLifecycle()
    var showBackupParts by remember { mutableStateOf(false) }
    var saveParts by remember { mutableStateOf(BackupParts()) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveBackup(uri, saveParts)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readBackup(uri)
    }

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
                    Text("نسخهٔ اپ: ${BuildConfig.VERSION_NAME.faDigits()}", style = MaterialTheme.typography.bodyMedium)
                    when (update.phase) {
                        UpdatePhase.CHECKING -> Text("در حال بررسی نسخهٔ جدید…", style = MaterialTheme.typography.bodySmall)
                        UpdatePhase.AVAILABLE, UpdatePhase.READY, UpdatePhase.ERROR -> OutlinedButton(
                            onClick = { showSettings = false; vm.showUpdateDialog() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("نسخهٔ جدید ${update.info?.versionName.orEmpty().faDigits()} آماده است") }
                        UpdatePhase.DOWNLOADING -> OutlinedButton(
                            onClick = { showSettings = false; vm.showUpdateDialog() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("در حال دانلود نسخهٔ جدید…") }
                        UpdatePhase.IDLE -> OutlinedButton(
                            onClick = { vm.checkForUpdate(manual = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("بررسی نسخهٔ جدید") }
                    }
                    if (isAdmin) {
                        HorizontalDivider()
                        Text("پشتیبان‌گیری", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "درس‌ها (با عکس)، برنامهٔ هفتگی، تکالیف، اعلان‌ها و لیست دانش‌آموزان در یک فایل روی گوشی ذخیره می‌شود. اگر اطلاعات کلاس از بین رفت، با همین فایل برمی‌گردد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            enabled = backupBusy == null,
                            onClick = {
                                showSettings = false
                                showBackupParts = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("ذخیرهٔ پشتیبان در گوشی") }
                        OutlinedButton(
                            enabled = backupBusy == null,
                            onClick = {
                                showSettings = false
                                openLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("بازیابی از فایل پشتیبان") }
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

    backupBusy?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(label) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
        )
    }

    if (showBackupParts) {
        BackupPartsDialog(
            onDismiss = { showBackupParts = false },
            onContinue = { parts ->
                saveParts = parts
                showBackupParts = false
                saveLauncher.launch("Classify-backup-${LocalDate.now()}.json")
            },
        )
    }

    backupPreview?.let { p ->
        RestoreDialog(p, onConfirm = { parts -> vm.applyRestore(parts) }, onDismiss = { vm.cancelRestore() })
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

@Composable
private fun PartRow(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** انتخاب اینکه چه چیزهایی در پشتیبان بیاید. */
@Composable
private fun BackupPartsDialog(onDismiss: () -> Unit, onContinue: (BackupParts) -> Unit) {
    var parts by remember { mutableStateOf(BackupParts()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("چه چیزهایی ذخیره شود؟") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PartRow("درس‌ها و برنامهٔ هفتگی", null, parts.schedule) { parts = parts.copy(schedule = it) }
                PartRow("تکالیف", null, parts.homework) { parts = parts.copy(homework = it) }
                PartRow("اعلان‌ها", null, parts.announcements) { parts = parts.copy(announcements = it) }
                PartRow("لیست دانش‌آموزان", null, parts.students) { parts = parts.copy(students = it) }
                PartRow("عکس درس‌ها", "حجم فایل را خیلی بیشتر می‌کند", parts.photos) { parts = parts.copy(photos = it) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "نام درس‌ها همیشه همراه تکالیف و عکس‌ها در فایل می‌آید تا هر چیز به درس خودش وصل شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = parts.any, onClick = { onContinue(parts) }) { Text("ادامه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** نمایش خلاصهٔ فایل و انتخاب اینکه چه چیزهایی بازیابی شود (فقط اجزایی که داخل فایل هست). */
@Composable
private fun RestoreDialog(p: BackupPreview, onConfirm: (BackupParts) -> Unit, onDismiss: () -> Unit) {
    val has = BackupParts(
        schedule = p.courses > 0 || p.slots > 0,
        homework = p.homework > 0,
        announcements = p.announcements > 0,
        students = p.students > 0,
        photos = p.photos > 0,
    )
    var parts by remember(p) { mutableStateOf(has) }
    val date = if (p.createdAt > 0) {
        JalaliCalendar.format(Instant.ofEpochMilli(p.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())
    } else {
        "نامشخص"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازیابی از پشتیبان") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("پشتیبان کلاس «${p.className.ifBlank { "نامشخص" }}» ، تاریخ $date")
                if (!p.sameClass) {
                    Text(
                        "هشدار: این پشتیبان برای کلاس دیگری ساخته شده است.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("چه چیزهایی بازیابی شود؟", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                PartRow("درس‌ها و برنامهٔ هفتگی", "${p.courses.fa()} درس، ${p.slots.fa()} ساعت کلاس", parts.schedule, has.schedule) {
                    parts = parts.copy(schedule = it)
                }
                PartRow("تکالیف", "${p.homework.fa()} تکلیف", parts.homework, has.homework) { parts = parts.copy(homework = it) }
                PartRow("اعلان‌ها", "${p.announcements.fa()} اعلان", parts.announcements, has.announcements) {
                    parts = parts.copy(announcements = it)
                }
                PartRow("لیست دانش‌آموزان", "${p.students.fa()} نفر", parts.students, has.students) { parts = parts.copy(students = it) }
                PartRow("عکس درس‌ها", "${p.photos.fa()} عکس", parts.photos, has.photos) { parts = parts.copy(photos = it) }
                Text(
                    "موارد جدید اضافه می‌شوند؛ چیزی پاک یا عوض نمی‌شود. درسی که هم‌نام یا هم‌شناسه با درس فعلی باشد دوباره اضافه نمی‌شود. " +
                        "تکلیف و عکسِ درسی که در کلاس نیست، بدون «درس‌ها و برنامه» بازیابی نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = parts.any, onClick = { onConfirm(parts) }) { Text("بازیابی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1_048_576.0).faDigits()

/** پیشنهاد آپدیت، دانلود با نوار پیشرفت، و نصب. */
@Composable
private fun UpdateDialog(
    state: UpdateUi,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
) {
    val info = state.info ?: return
    val sameName = info.versionName == BuildConfig.VERSION_NAME
    val versionText = info.versionName.faDigits() + if (sameName) " (ساخت ${info.versionCode.fa()})" else ""
    when (state.phase) {
        UpdatePhase.DOWNLOADING -> AlertDialog(
            onDismissRequest = {},
            title = { Text("در حال دانلود نسخهٔ $versionText") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val total = if (state.total > 0) state.total else info.size
                    val fraction = if (total > 0) (state.done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${megabytes(state.done)} از ${megabytes(total)} مگابایت",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onCancel) { Text("لغو دانلود") } },
        )
        UpdatePhase.READY -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("دانلود کامل شد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("برای نصب نسخهٔ $versionText دکمهٔ «نصب» را بزنید؛ اندروید یک بار تأیید می‌خواهد.")
                    if (state.needsPermission) {
                        Text(
                            "در صفحه‌ای که باز شد، اجازهٔ «نصب برنامه‌ها از این منبع» را روشن کنید و به اپ برگردید.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onInstall) { Text("نصب") } },
            dismissButton = { TextButton(onClick = onLater) { Text("بعداً") } },
        )
        UpdatePhase.ERROR -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("آپدیت انجام نشد") },
            text = { Text(state.error ?: "خطای ناشناخته", color = MaterialTheme.colorScheme.error) },
            confirmButton = { TextButton(onClick = onDownload) { Text("تلاش دوباره") } },
            dismissButton = { TextButton(onClick = onLater) { Text("بستن") } },
        )
        else -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("نسخهٔ جدید آماده است") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "نسخهٔ $versionText ، حجم حدود ${megabytes(info.size)} مگابایت",
                        fontWeight = FontWeight.Bold,
                    )
                    if (info.changelog.isNotEmpty()) {
                        Text("تازه‌ها:", style = MaterialTheme.typography.labelLarge)
                        info.changelog.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Text(
                        "بعد از دانلود، اندروید برای نصب از شما تأیید می‌گیرد. اطلاعات شما پاک نمی‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDownload) { Text("دانلود و نصب") } },
            dismissButton = { TextButton(onClick = onLater) { Text("بعداً") } },
        )
    }
}

/** بعد از آپدیت: تازه‌های نسخه(های) جدید. */
@Composable
private fun WhatsNewDialog(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تازه‌های Classify") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                entries.forEach { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("نسخهٔ ${e.version.faDigits()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        e.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("متوجه شدم") } },
    )
}
