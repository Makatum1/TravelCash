package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CoralRedDark
import com.example.ui.theme.EmeraldGreenDark
import com.example.ui.theme.OceanNavy
import com.example.ui.theme.TravelBlue

@Composable
fun SettingsTabScreen(
    themeMode: AppThemeMode,
    language: AppLanguage,
    reminderNotificationsEnabled: Boolean,
    tripCount: Int,
    participantCount: Int,
    expenseCount: Int,
    incomeCount: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onReminderNotificationsChange: (Boolean) -> Unit,
    onImportCsvClick: () -> Unit,
    onImportPdfClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onExportBackupJsonClick: () -> Unit,
    onRestoreBackupJsonClick: (replaceAll: Boolean) -> Unit,
    onOpenBackupModalClick: () -> Unit
) {
    val context = LocalContext.current
    val isEn = language == AppLanguage.EN

    // Todos los desplegables de Ajustes están por defecto CERRADOS (false)
    var importExportExpanded by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    var replaceAllMode by remember { mutableStateOf(false) }

    // Permiso POST_NOTIFICATIONS en Android 13+ al activar las notificaciones de recordatorio
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onReminderNotificationsChange(true)
    }

    fun toggleReminderNotifications(newValue: Boolean) {
        if (newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        onReminderNotificationsChange(newValue)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // =========================================================================
        // 1. IMPORTAR / EXPORTAR (CSV Y PDF) - CERRADO POR DEFECTO
        // =========================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isEn) "Import / Export (CSV & PDF)" else "Importar / Exportar (CSV y PDF)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEn) {
                            "Import or export active trip data in CSV or PDF format:"
                        } else {
                            "Importa o exporta los datos del viaje en formato CSV o PDF:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Botón desplegable principal "Importar / Exportar" (cerrado por defecto)
                    Button(
                        onClick = { importExportExpanded = !importExportExpanded },
                        colors = ButtonDefaults.buttonColors(containerColor = TravelBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_import_main_button")
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "Import / Export" else "Importar / Exportar",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (importExportExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = importExportExpanded) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Bloque IMPORTAR (CSV y PDF)
                                Text(
                                    text = if (isEn) "📥 Import to Trip:" else "📥 Importar al viaje:",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onImportCsvClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenDark),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_import_csv_button")
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isEn) "Import CSV" else "Importar CSV",
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Button(
                                        onClick = onImportPdfClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralRedDark),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_import_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isEn) "Import PDF" else "Importar PDF",
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                // Bloque EXPORTAR (CSV y PDF)
                                Text(
                                    text = if (isEn) "📤 Export Active Trip:" else "📤 Exportar viaje activo:",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onExportCsvClick,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.5.dp, EmeraldGreenDark),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreenDark),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_export_csv_button")
                                    ) {
                                        Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isEn) "Export CSV" else "Exportar CSV",
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onExportPdfClick,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.5.dp, CoralRedDark),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRedDark),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_export_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isEn) "Export PDF" else "Exportar PDF",
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. COPIA DE SEGURIDAD JSON (BACKUP) - CERRADO POR DEFECTO
        // =========================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = TravelBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isEn) "JSON Backup (Export & Restore)" else "Copia de Seguridad JSON (Backup)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isEn) {
                                        "$tripCount trips · $participantCount travelers · $expenseCount expenses · $incomeCount pot incomes"
                                    } else {
                                        "$tripCount viajes · $participantCount viajeros · $expenseCount gastos · $incomeCount ingresos bote"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { backupExpanded = !backupExpanded },
                        colors = ButtonDefaults.buttonColors(containerColor = OceanNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_backup_main_button")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "Backup (Import / Export JSON)" else "Backup (Importar / Exportar JSON)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (backupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = backupExpanded) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Exportar Backup JSON
                                Button(
                                    onClick = onExportBackupJsonClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = TravelBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_export_backup_json_button")
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "Export Full Backup (.json)" else "Exportar Backup Total (.json)",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                // Modo de Importación / Restauración: Fusionar vs Reemplazar
                                Text(
                                    text = if (isEn) "Restore / Import JSON Mode:" else "Modo al Importar / Restaurar JSON:",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = !replaceAllMode,
                                        onClick = { replaceAllMode = false }
                                    )
                                    Column {
                                        Text(
                                            text = if (isEn) "Merge with current data" else "Fusionar con datos actuales",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isEn) {
                                                "Keeps current trips and adds or updates trips from the backup."
                                            } else {
                                                "Conserva tus viajes actuales y añade o actualiza los del archivo."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = replaceAllMode,
                                        onClick = { replaceAllMode = true }
                                    )
                                    Column {
                                        Text(
                                            text = if (isEn) "Replace all data" else "Reemplazar todo",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = CoralRed
                                        )
                                        Text(
                                            text = if (isEn) {
                                                "Deletes current data before restoring the backup."
                                            } else {
                                                "Borra los datos actuales antes de restaurar la copia."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onRestoreBackupJsonClick(replaceAllMode) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenDark),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_restore_backup_json_button")
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "Import / Restore Backup (.json)" else "Importar / Restaurar Backup (.json)",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenBackupModalClick,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_open_backup_modal_button")
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) {
                                            "Advanced JSON Manager (Copy / Paste / Preview)"
                                        } else {
                                            "Gestor Avanzado JSON (Copiar / Pegar / Vista previa)"
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. NOTIFICACIONES DE RECORDATORIO (SWITCH LIMPIO SIN DESPLEGABLE)
        // =========================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (reminderNotificationsEnabled) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (reminderNotificationsEnabled) EmeraldGreenDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEn) "Reminder Notifications" else "Notificaciones de Recordatorio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (reminderNotificationsEnabled) {
                                    if (isEn) "Active — Reminders to record daily expenses" else "Activadas — Recordatorios para anotar gastos diarios"
                                } else {
                                    if (isEn) "Disabled" else "Desactivadas"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reminderNotificationsEnabled) EmeraldGreenDark else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = reminderNotificationsEnabled,
                        onCheckedChange = { checked ->
                            toggleReminderNotifications(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldGreenDark
                        ),
                        modifier = Modifier.testTag("settings_reminder_notifications_switch")
                    )
                }
            }
        }

        // =========================================================================
        // 4. MODO DE TEMA: CLARO (PREDETERMINADO), OSCURO O SISTEMA - CERRADO POR DEFECTO
        // =========================================================================
        item {
            val currentThemeLabel = when (themeMode) {
                AppThemeMode.LIGHT -> if (isEn) "Light Mode (Default)" else "Modo Claro (Predeterminado)"
                AppThemeMode.DARK -> if (isEn) "Dark Mode" else "Modo Oscuro"
                AppThemeMode.SYSTEM -> if (isEn) "System Default" else "Modo Sistema"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { appearanceExpanded = !appearanceExpanded }
                            .testTag("settings_appearance_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brightness4,
                                contentDescription = null,
                                tint = TravelBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isEn) "Appearance (Theme Mode)" else "Apariencia (Modo de Pantalla)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentThemeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = if (appearanceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = appearanceExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            val themeOptions = listOf(
                                Triple(
                                    AppThemeMode.LIGHT,
                                    if (isEn) "Light Mode (Default)" else "Modo Claro (Predeterminado)",
                                    Icons.Default.LightMode
                                ),
                                Triple(
                                    AppThemeMode.DARK,
                                    if (isEn) "Dark Mode" else "Modo Oscuro",
                                    Icons.Default.DarkMode
                                ),
                                Triple(
                                    AppThemeMode.SYSTEM,
                                    if (isEn) "System Default" else "Modo Sistema",
                                    Icons.Default.SettingsSuggest
                                )
                            )

                            themeOptions.forEach { (mode, label, icon) ->
                                val selected = themeMode == mode
                                Surface(
                                    onClick = { onThemeModeChange(mode) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) TravelBlue.copy(alpha = 0.12f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { onThemeModeChange(mode) }
                                        )
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (selected) TravelBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 5. IDIOMA: ESPAÑOL (PREDETERMINADO) E INGLÉS - CERRADO POR DEFECTO
        // =========================================================================
        item {
            val currentLangLabel = if (isEn) language.displayNameEn else language.displayNameEs

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { languageExpanded = !languageExpanded }
                            .testTag("settings_language_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = EmeraldGreenDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isEn) "App Language" else "Idioma de la Aplicación",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (language == AppLanguage.ES) "🇪🇸 $currentLangLabel" else "🇬🇧 $currentLangLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = if (languageExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = languageExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                val selected = language == lang
                                val label = if (isEn) lang.displayNameEn else lang.displayNameEs
                                Surface(
                                    onClick = { onLanguageChange(lang) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) EmeraldGreenDark.copy(alpha = 0.12f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { onLanguageChange(lang) }
                                        )
                                        Text(
                                            text = if (lang == AppLanguage.ES) "🇪🇸 $label" else "🇬🇧 $label",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
