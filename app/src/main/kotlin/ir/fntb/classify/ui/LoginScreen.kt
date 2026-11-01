package ir.fntb.classify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.fntb.classify.R
import ir.fntb.classify.data.MainViewModel
import ir.fntb.classify.data.Role
import ir.fntb.classify.data.ServerConfig
import ir.fntb.classify.util.enDigits

@Composable
fun LoginScreen(vm: MainViewModel) {
    val state by vm.login.collectAsStateWithLifecycle()
    val classes by vm.classes.collectAsStateWithLifecycle()
    // اپ دانش‌آموز فقط ورود دانش‌آموز دارد؛ اپ مدیریت فقط مدیر کلاس و مدیر کل
    val availableRoles = if (ServerConfig.isManagerApp) listOf(Role.MANAGER, Role.SUPER) else listOf(Role.STUDENT)
    var roleName by rememberSaveable { mutableStateOf(availableRoles.first().name) }
    var classId by rememberSaveable { mutableStateOf(vm.lastValue("classId")) }
    var first by rememberSaveable { mutableStateOf(vm.lastValue("first")) }
    var last by rememberSaveable { mutableStateOf(vm.lastValue("last")) }
    var secret by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val role = Role.valueOf(roleName)
    val selectedClass = classes.list.firstOrNull { it.id == classId }

    LaunchedEffect(Unit) { if (ServerConfig.isConfigured) vm.loadClasses() }

    val canSubmit = !state.busy && ServerConfig.isConfigured && when (role) {
        Role.STUDENT -> classId.isNotBlank() && first.isNotBlank() && last.isNotBlank() &&
            (!state.needsCode || code.isNotBlank())
        Role.MANAGER -> classId.isNotBlank() && secret.isNotBlank()
        Role.SUPER -> secret.isNotBlank()
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (ServerConfig.isManagerApp) "Classify Manager" else "Classify",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                if (ServerConfig.isManagerApp) "مدیریت کلاس، برنامه و تکالیف" else "برنامه و تکالیف کلاس",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (availableRoles.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableRoles.forEach { r ->
                        FilterChip(
                            selected = r == role,
                            onClick = { roleName = r.name; secret = ""; code = ""; vm.cancelCode() },
                            label = { Text(r.label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!ServerConfig.isConfigured) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "این نسخه از اپ به سرور وصل نشده است. نسخهٔ درست را از سازندهٔ اپ بگیرید.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (role != Role.SUPER) {
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "کلاس",
                    options = classes.list,
                    selected = selectedClass,
                    text = { it.name },
                ) { classId = it.id }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        enabled = ServerConfig.isConfigured && !classes.busy,
                        onClick = { vm.loadClasses() },
                    ) { Text("دریافت لیست کلاس‌ها") }
                    if (classes.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                classes.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (classes.loaded && classes.list.isEmpty()) {
                    Text(
                        "هنوز کلاسی روی این سرور ساخته نشده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (role == Role.STUDENT) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text("نام") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = last,
                    onValueChange = { last = it },
                    label = { Text("نام خانوادگی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.needsCode) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "این نام روی دستگاه دیگری فعال است.",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "در آن دستگاه، تنظیمات (⚙️) ← «کد تأیید برای دستگاه جدید» را بزنید و کد ۶ رقمی را اینجا وارد کنید. اگر آن دستگاه را ندارید، از مدیر کلاس بخواهید قفل را آزاد کند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.enDigits().filter { c -> c.isDigit() }.take(6) },
                                label = { Text("کد تأیید") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = { code = ""; vm.cancelCode() }) { Text("انصراف") }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(if (role == Role.SUPER) "رمز مدیر کل" else "رمز مدیر کلاس") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    when (role) {
                        Role.STUDENT ->
                            if (state.needsCode) vm.verifyStudent(classId, first, last, code)
                            else vm.loginStudent(classId, first, last)
                        Role.MANAGER -> vm.loginManager(classId, secret)
                        Role.SUPER -> vm.loginSuper(secret)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (role == Role.STUDENT && state.needsCode) "تأیید دستگاه" else "ورود")
                }
            }
        }
    }
}
