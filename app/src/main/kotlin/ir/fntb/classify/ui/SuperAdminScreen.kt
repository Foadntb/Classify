@file:OptIn(ExperimentalMaterial3Api::class)

package ir.fntb.classify.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.fntb.classify.data.ClassInfo
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.util.fa
import kotlinx.coroutines.launch

/** صفحهٔ مدیر کل: ساخت کلاس، تعیین رمز مدیر هر کلاس، تغییر نام و حذف. */
@Composable
fun SuperAdminScreen(vm: MainViewModel) {
    val state by vm.adminClasses.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClassInfo?>(null) }
    var deleting by remember { mutableStateOf<ClassInfo?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadAdminClasses() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("مدیریت کلاس‌ها") },
                actions = {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.loadAdminClasses() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "به‌روزرسانی")
                        }
                    }
                    TextButton(onClick = { confirmLogout = true }) { Text("خروج") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("کلاس جدید") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "برای هر کلاس یک رمز مدیر تعیین کنید و آن را به مدیر همان کلاس بدهید. مدیر کلاس، دانش‌آموزان و برنامه و تکالیف همان کلاس را خودش مدیریت می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.list.isEmpty() && state.loaded) {
                    EmptyState(
                        icon = Icons.Outlined.Groups,
                        text = "هنوز کلاسی ساخته نشده است. با «کلاس جدید» شروع کنید، مثلاً «دهم گرافیک».",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(state.list, key = { it.id }) { c ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("${c.students.fa()} دانش‌آموز") },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editing = c }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "ویرایش")
                                        }
                                        IconButton(onClick = { deleting = c }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "حذف")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ClassDialog(
            initial = null,
            onDismiss = { creating = false },
            onSubmit = { name, pw, done -> vm.createClass(name, pw, done) },
        )
    }
    editing?.let { c ->
        ClassDialog(
            initial = c,
            onDismiss = { editing = null },
            onSubmit = { name, pw, done -> vm.updateClass(c.id, name, pw.ifBlank { null }, done) },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "حذف «${c.name}»؟",
            message = "همهٔ دانش‌آموزان، درس‌ها، برنامه و تکالیف این کلاس برای همیشه پاک می‌شوند.",
            confirmLabel = "حذف کلاس",
            onConfirm = {
                deleting = null
                vm.deleteClass(c.id) { err -> if (err != null) scope.launch { snackbar.showSnackbar(err) } }
            },
            onDismiss = { deleting = null },
        )
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "خروج از حساب مدیر کل؟",
            message = "برای مدیریت کلاس‌ها باید دوباره وارد شوید.",
            confirmLabel = "خروج",
            onConfirm = { confirmLogout = false; vm.logout() },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun ClassDialog(
    initial: ClassInfo?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, password: String, done: (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val passwordOk = if (initial == null) password.trim().length >= 4 else password.isBlank() || password.trim().length >= 4

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "کلاس جدید" else "ویرایش کلاس") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام کلاس") },
                    placeholder = { Text("دهم گرافیک") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initial == null) "رمز مدیر کلاس" else "رمز جدید مدیر کلاس (اختیاری)") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    supportingText = { Text("حداقل ۴ نویسه. با تغییر رمز، نشست قبلی مدیر آن کلاس باطل می‌شود.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank() && passwordOk,
                onClick = {
                    busy = true
                    error = null
                    onSubmit(name, password) { err ->
                        busy = false
                        if (err == null) onDismiss() else error = err
                    }
                },
            ) { Text(if (busy) "…" else "ذخیره") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
    )
}
