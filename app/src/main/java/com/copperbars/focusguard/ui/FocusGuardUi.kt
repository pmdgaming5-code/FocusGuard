package com.copperbars.focusguard.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copperbars.focusguard.LaunchableApp
import com.copperbars.focusguard.MainActivity
import com.copperbars.focusguard.admin.KioskController
import com.copperbars.focusguard.admin.KioskResult
import com.copperbars.focusguard.data.AppSettings
import com.copperbars.focusguard.data.DailyUsageEntity
import com.copperbars.focusguard.data.FocusRepository
import com.copperbars.focusguard.data.FocusSessionEntity
import com.copperbars.focusguard.data.TrackedAppEntity
import com.copperbars.focusguard.security.PinSecurity
import com.copperbars.focusguard.security.PinCheck
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class AppTab(val label: String, val symbol: String) {
    HOME("Ana Sayfa", "⌂"),
    APPS("Uygulamalar", "▦"),
    FOCUS("Odak", "◷"),
    DEVICE("Cihaz Kilidi", "▣"),
    STATS("İstatistik", "▤"),
    SETTINGS("Ayarlar", "⚙")
}

@Composable
fun FocusGuardTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF9BA4FF),
            onPrimary = Color(0xFF10132B),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF0D1220),
            surface = Color(0xFF171F32),
            surfaceVariant = Color(0xFF242E44),
            onSurface = Color(0xFFE9EDF7),
            onBackground = Color(0xFFE9EDF7)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4B55BD),
            secondary = Color(0xFF087F73),
            background = Color(0xFFF5F6FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE9ECF7)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun FocusGuardRoot(
    activity: MainActivity,
    repository: FocusRepository,
    launchableApps: List<LaunchableApp>,
    hasUsageAccess: () -> Boolean,
    hasOverlayAccess: () -> Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartMonitoring: () -> String,
    onStopMonitoring: () -> Unit
) {
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val trackedApps by repository.trackedApps.collectAsState(initial = emptyList())
    val usage by repository.todayUsage().collectAsState(initial = emptyList())
    val sessions by repository.recentSessions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }

    FocusGuardTheme(darkTheme = settings.darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(tab.symbol, fontSize = 17.sp) },
                            label = { Text(tab.label, fontSize = 9.sp, maxLines = 1) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (selectedTab) {
                    AppTab.HOME -> HomeScreen(
                        settings = settings,
                        trackedApps = trackedApps,
                        usage = usage,
                        usageAccess = hasUsageAccess(),
                        overlayAccess = hasOverlayAccess(),
                        onStart = {
                            val result = onStartMonitoring()
                            Toast.makeText(activity, result, Toast.LENGTH_LONG).show()
                        },
                        onStop = onStopMonitoring,
                        onOpenSettings = { selectedTab = AppTab.SETTINGS }
                    )
                    AppTab.APPS -> AppsScreen(
                        installedApps = launchableApps,
                        trackedApps = trackedApps,
                        onSave = { scope.launch { repository.saveTrackedApp(it) } },
                        onRemove = { scope.launch { repository.removeTrackedApp(it) } }
                    )
                    AppTab.FOCUS -> FocusScreen(
                        settings = settings,
                        trackedApps = trackedApps,
                        onStart = { minutes ->
                            scope.launch { repository.startFocusSession(minutes) }
                            val result = onStartMonitoring()
                            Toast.makeText(activity, result, Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { scope.launch { repository.cancelFocusSession() } }
                    )
                    AppTab.DEVICE -> DeviceLockScreen(
                        activity = activity,
                        repository = repository,
                        trackedApps = trackedApps
                    )
                    AppTab.STATS -> StatisticsScreen(
                        usage = usage,
                        trackedApps = trackedApps,
                        sessions = sessions
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        activity = activity,
                        repository = repository,
                        settings = settings,
                        usageAccess = hasUsageAccess(),
                        overlayAccess = hasOverlayAccess(),
                        onOpenUsageSettings = onOpenUsageSettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onMonitorStart = {
                            val result = onStartMonitoring()
                            Toast.makeText(activity, result, Toast.LENGTH_LONG).show()
                        },
                        onMonitorStop = onStopMonitoring
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    settings: AppSettings,
    trackedApps: List<TrackedAppEntity>,
    usage: List<DailyUsageEntity>,
    usageAccess: Boolean,
    overlayAccess: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val total = usage.sumOf { it.totalMillis }
    val top = usage.maxByOrNull { it.totalMillis }
    val activeFocus = settings.focusUntilMillis > System.currentTimeMillis()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FOCUS GUARD", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Kontrol sende.", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Dijital alışkanlıklarını yönet, hedeflerine odaklan.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Bugünkü kullanım", formatDuration(total), Modifier.weight(1f))
            MetricCard("Takip edilen", trackedApps.size.toString(), Modifier.weight(1f))
        }
        MetricCard(
            "En çok kullanılan",
            top?.let { app -> trackedApps.firstOrNull { it.packageName == app.packageName }?.label ?: app.packageName }
                ?: "Henüz veri yok",
            Modifier.fillMaxWidth(),
            formatDuration(top?.totalMillis ?: 0L)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("İzin ve takip durumu", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                StatusLine("Kullanım erişimi", usageAccess)
                StatusLine("Diğer uygulamaların üzerinde gösterme", overlayAccess)
                StatusLine("PIN ayarlandı", settings.pinVerifier.isNotBlank())
                StatusLine("Takip servisi", settings.monitorEnabled)
                if (!usageAccess || !overlayAccess || settings.pinVerifier.isBlank()) {
                    Text(
                        "Süre dolduğunda uygulama engelinin çalışması için yukarıdaki izinleri ve PIN kurulumunu tamamla.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Kurulumu tamamla")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (activeFocus) "Odak modu etkin" else "Odak modu hazır", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (activeFocus) {
                        "Kalan süre: " + formatDuration((settings.focusUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L))
                    } else {
                        "25, 45 veya 60 dakikalık bir odak oturumu başlatabilirsin."
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        Button(
            onClick = if (settings.monitorEnabled) onStop else onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (settings.monitorEnabled) "Takibi duraklat" else "Uygulama takibini başlat")
        }
        Text(
            "FocusGuard yerel çalışır. Android'in sistem güvenliği ve acil işlevleri üzerinde kontrol iddiasında bulunmaz.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, subtitle: String = "") {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp)
        Text(if (ok) "Hazır" else "Gerekli", color = if (ok) Color(0xFF65C99A) else Color(0xFFFFB86B), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun AppsScreen(
    installedApps: List<LaunchableApp>,
    trackedApps: List<TrackedAppEntity>,
    onSave: (TrackedAppEntity) -> Unit,
    onRemove: (String) -> Unit
) {
    var editing by remember { mutableStateOf<TrackedAppEntity?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Uygulama süreleri", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Uygulama kullanım sayacı yalnızca hedef uygulama ön plandayken ilerler. Eklediğin uygulamaya dokunarak limit belirle.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        if (installedApps.isEmpty()) {
            Text("Açılabilir uygulama listesi alınamadı. Cihazdaki uygulama listesini kontrol et.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(installedApps, key = { it.packageName }) { info ->
                    val config = trackedApps.firstOrNull { it.packageName == info.packageName }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(info.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (config == null) "Henüz sınır yok"
                                    else "Günlük " + limitText(config.dailyLimitMinutes) + " · Oturum " + limitText(config.sessionLimitMinutes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                                if (config?.focusBlocked == true) {
                                    Text("Odak modunda engellenecek", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (config == null) {
                                Button(onClick = {
                                    onSave(
                                        TrackedAppEntity(
                                            packageName = info.packageName,
                                            label = info.label,
                                            dailyLimitMinutes = 5
                                        )
                                    )
                                }) { Text("Ekle") }
                            } else {
                                Column(horizontalAlignment = Alignment.End) {
                                    Switch(
                                        checked = true,
                                        onCheckedChange = { checked ->
                                            if (!checked) onRemove(info.packageName)
                                        }
                                    )
                                    TextButton(onClick = { editing = config }) { Text("Düzenle") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { current ->
        LimitDialog(
            app = current,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            }
        )
    }
}

@Composable
private fun LimitDialog(
    app: TrackedAppEntity,
    onDismiss: () -> Unit,
    onSave: (TrackedAppEntity) -> Unit
) {
    var daily by remember(app.packageName) { mutableStateOf(app.dailyLimitMinutes.toString()) }
    var session by remember(app.packageName) { mutableStateOf(app.sessionLimitMinutes.toString()) }
    var focusBlocked by remember(app.packageName) { mutableStateOf(app.focusBlocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label + " limitleri") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = daily,
                    onValueChange = { daily = it.filter(Char::isDigit).take(4) },
                    label = { Text("Günlük limit (dakika)") },
                    supportingText = { Text("0 = sınırsız") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = session,
                    onValueChange = { session = it.filter(Char::isDigit).take(4) },
                    label = { Text("Tek oturum limiti (dakika)") },
                    supportingText = { Text("0 = sınırsız") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Odak modunda engelle", modifier = Modifier.weight(1f))
                    Switch(checked = focusBlocked, onCheckedChange = { focusBlocked = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val dailyValue = daily.toIntOrNull()?.coerceIn(0, 1440) ?: app.dailyLimitMinutes
                val sessionValue = session.toIntOrNull()?.coerceIn(0, 1440) ?: app.sessionLimitMinutes
                onSave(app.copy(
                    dailyLimitMinutes = dailyValue,
                    sessionLimitMinutes = sessionValue,
                    focusBlocked = focusBlocked
                ))
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun FocusScreen(
    settings: AppSettings,
    trackedApps: List<TrackedAppEntity>,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var minutes by remember { mutableStateOf("25") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val active = settings.focusUntilMillis > now
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Odak modu", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (active) "KALAN SÜRE" else "HAZIR OLDUĞUNDA BAŞLAT", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (active) formatDuration(settings.focusUntilMillis - now) else minutes + " dakika",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                if (active) {
                    Text("Odak oturumu devam ediyor.", fontSize = 13.sp)
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Text("Oturumu bitir")
                    }
                } else {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text("Süre (dakika)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(25, 45, 60, 90).forEach { value ->
                            OutlinedButton(onClick = { minutes = value.toString() }) { Text(value.toString()) }
                        }
                    }
                    Button(
                        onClick = { onStart(minutes.toIntOrNull()?.coerceIn(1, 240) ?: 25) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("Odaklanmayı başlat") }
                }
            }
        }
        Text("Odak modunda engellenecek uygulamalar", fontWeight = FontWeight.SemiBold)
        val blocked = trackedApps.filter { it.focusBlocked }
        if (blocked.isEmpty()) {
            Text("Henüz uygulama seçilmedi. Uygulamalar sekmesinden 'Odak modunda engelle' seçeneğini aç.")
        } else {
            blocked.forEach {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(it.label, modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }
        Text(
            "Oturum bitişi WorkManager ile kaydedilir; cihaz yeniden başlasa da planlanan bitiş zamanı korunur.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun DeviceLockScreen(
    activity: Activity,
    repository: FocusRepository,
    trackedApps: List<TrackedAppEntity>
) {
    var confirm by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var pinToExit by remember { mutableStateOf("") }
    var exitFeedback by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val owner = remember { KioskController.isDeviceOwner(activity) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val pinSecurity = remember { PinSecurity(repository) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Cihaz kilidi", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Standart odak kilidi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Seçtiğin uygulamalar Usage Access ve ekran üstü uyarı ile engellenir. Normal Android uygulaması tüm sistemi kilitleyemez.",
                    fontSize = 13.sp
                )
                Text("Kiosk / Lock Task durumu: " + if (owner) "Device Owner hazır" else "Device Owner yetkisi yok")
                if (!owner) {
                    Text(
                        "Gerçek kiosk modu için cihazın önceden Device Owner olarak kurulması gerekir. Bu işlem normal bir izin penceresi değildir ve genellikle yeni/sıfırlanmış bir test cihazında yapılır.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { confirm = true },
                    enabled = owner && settings.pinVerifier.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kiosk modunu başlat") }
                if (settings.pinVerifier.isBlank()) {
                    Text("Kiosk modundan PIN ile çıkış yapabilmek için önce Ayarlar bölümünde PIN oluştur.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { confirmExit = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("PIN ile kiosk modundan çık") }
                if (info.isNotBlank()) Text(info, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
        Text(
            "Kiosk izin listesi, Odak modunda engellenmeyen takipli uygulamalardan oluşur. Android Ayarları ve telefon arama uygulamaları kurtarma amacıyla listeye eklenir.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false; pinToExit = "" },
            title = { Text("Kiosk modundan çıkış") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Devam etmek için FocusGuard PIN kodunu gir.")
                    if (exitFeedback.isNotBlank()) Text(exitFeedback, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    OutlinedTextField(
                        value = pinToExit,
                        onValueChange = { pinToExit = it.filter(Char::isDigit).take(12) },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        when (val check = pinSecurity.verify(pinToExit)) {
                            PinCheck.Success -> {
                                KioskController.stop(activity)
                                info = "PIN doğrulandı. Kiosk modundan çıkış istendi."
                                exitFeedback = ""
                                confirmExit = false
                                pinToExit = ""
                            }
                            PinCheck.Invalid -> exitFeedback = "PIN yanlış."
                            PinCheck.NotConfigured -> exitFeedback = "PIN ayarlanmamış."
                            is PinCheck.Locked -> {
                                val seconds = ((check.untilMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L) + 1
                                exitFeedback = "Çok fazla hatalı deneme. $seconds saniye bekle."
                            }
                        }
                    }
                }) { Text("Doğrula ve çık") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; pinToExit = "" }) { Text("İptal") }
            }
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Kiosk modunu etkinleştir?") },
            text = {
                Text("Bu özellik cihaz arayüzünü kısıtlayabilir. Yalnızca kontrol ettiğin test cihazında kullan. FocusGuard, Ayarlar ve telefon uygulaması izin listesinde tutulacaktır.")
            },
            confirmButton = {
                Button(onClick = {
                    val allowed = trackedApps.filter { !it.focusBlocked }.map { it.packageName } +
                        listOf("com.android.settings", "com.android.phone")
                    info = when (val result = KioskController.start(activity, allowed)) {
                        KioskResult.Started -> "Kiosk modu başlatıldı."
                        KioskResult.DeviceOwnerRequired -> "Bu cihaz Device Owner olarak yapılandırılmamış."
                        is KioskResult.Failed -> result.reason
                    }
                    confirm = false
                }) { Text("Başlat") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun StatisticsScreen(
    usage: List<DailyUsageEntity>,
    trackedApps: List<TrackedAppEntity>,
    sessions: List<FocusSessionEntity>
) {
    val labels = trackedApps.associate { it.packageName to it.label }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("İstatistikler", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Bugünkü uygulama kullanımı", fontWeight = FontWeight.SemiBold)
        if (usage.isEmpty()) {
            Text("Henüz kullanım kaydı yok.", modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(usage, key = { it.packageName }) { row ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(labels[row.packageName] ?: row.packageName, fontWeight = FontWeight.Medium)
                                Text(formatDuration(row.totalMillis), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(6.dp))
                            val total = usage.maxOfOrNull { it.totalMillis }?.coerceAtLeast(1L) ?: 1L
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (row.totalMillis.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Son odak oturumları", fontWeight = FontWeight.SemiBold)
        if (sessions.isEmpty()) {
            Text("Henüz odak oturumu tamamlanmadı.", modifier = Modifier.padding(vertical = 10.dp))
        } else {
            sessions.take(6).forEach { session ->
                Text(
                    (if (session.completed) "✓ " else "◷ ") + session.label + " · " +
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startedAtMillis)),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    activity: MainActivity,
    repository: FocusRepository,
    settings: AppSettings,
    usageAccess: Boolean,
    overlayAccess: Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onMonitorStart: () -> Unit,
    onMonitorStop: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pinSecurity = remember { PinSecurity(repository) }
    var message by remember(settings.warningMessage) { mutableStateOf(settings.warningMessage) }
    var pin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ayarlar", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gerekli Android izinleri", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                StatusLine("Kullanım erişimi", usageAccess)
                Button(onClick = onOpenUsageSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Kullanım erişimi ayarlarını aç")
                }
                StatusLine("Diğer uygulamaların üzerinde gösterme", overlayAccess)
                Button(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Üstte gösterme iznini aç")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Uyarı mesajı", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Süre dolduğunda göster") },
                    minLines = 2,
                    maxLines = 4
                )
                Button(
                    onClick = { scope.launch { repository.updateWarningMessage(message) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Mesajı kaydet") }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Şifre güvenliği", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (settings.pinVerifier.isBlank()) "Henüz PIN ayarlanmadı."
                    else "PIN kayıtlı. Değiştirmek için mevcut PIN'i doğrula ve yeni PIN'i iki kez gir.",
                    fontSize = 12.sp
                )
                if (settings.pinVerifier.isNotBlank()) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter(Char::isDigit).take(12) },
                        label = { Text("Mevcut PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("Yeni PIN (4-12 rakam)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN'i tekrar gir") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        scope.launch {
                            val oldPinValid = settings.pinVerifier.isBlank() ||
                                pinSecurity.verify(oldPin) is PinCheck.Success
                            feedback = when {
                                !oldPinValid -> "Mevcut PIN doğrulanamadı."
                                pin != confirmPin -> "PIN doğrulaması eşleşmedi."
                                pinSecurity.setPin(pin) -> {
                                    pin = ""
                                    oldPin = ""
                                    confirmPin = ""
                                    "PIN güvenli biçimde kaydedildi."
                                }
                                else -> "PIN 4-12 rakamdan oluşmalı."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("PIN'i kaydet / değiştir") }
                if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Koyu tema", fontWeight = FontWeight.SemiBold)
                        Text("Açık / koyu görünüm", fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.darkTheme,
                        onCheckedChange = { value -> scope.launch { repository.setDarkTheme(value) } }
                    )
                }
                Text("İzleme servisi", fontWeight = FontWeight.Bold)
                if (settings.monitorEnabled) {
                    OutlinedButton(onClick = onMonitorStop, modifier = Modifier.fillMaxWidth()) {
                        Text("Takibi duraklat")
                    }
                } else {
                    Button(onClick = onMonitorStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Takibi başlat")
                    }
                }
            }
        }

        Text(
            "PIN unutulursa uzaktan parola sıfırlama yoktur. Uygulama verilerini temizlemek yerel geçmişi ve PIN kaydını siler. Android Keystore anahtarı uygulama verileri silinince de kaldırılır.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
    }
}

private fun limitText(minutes: Int): String =
    if (minutes <= 0) "sınırsız" else minutes.toString() + " dk"

private fun formatDuration(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe % 3_600_000L) / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    return if (hours > 0) {
        hours.toString() + " sa " + minutes.toString() + " dk"
    } else if (minutes > 0) {
        minutes.toString() + " dk " + seconds.toString() + " sn"
    } else {
        seconds.toString() + " sn"
    }
}
