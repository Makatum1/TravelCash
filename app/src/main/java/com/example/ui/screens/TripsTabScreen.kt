package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ExpenseEntity
import com.example.data.model.IncomeEntity
import com.example.data.model.ParticipantEntity
import com.example.data.model.TripEntity
import com.example.ui.components.EditableTripRouteIcon
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CoralRedDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenDark
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OceanNavy
import com.example.ui.theme.TravelBlue
import com.example.util.FormatUtils
import java.time.LocalDate

@Composable
fun TripsTabScreen(
    trips: List<TripEntity>,
    activeTripId: String?,
    allParticipants: List<ParticipantEntity>,
    allExpenses: List<ExpenseEntity>,
    allIncomes: List<IncomeEntity>,
    onSelectTrip: (String) -> Unit,
    onTripIconClick: (TripEntity) -> Unit,
    onCreateNewTripClick: () -> Unit,
    onEditTripClick: (TripEntity) -> Unit = {},
    onExportAllTripsPdfClick: () -> Unit,
    onExportAllTripsCsvClick: () -> Unit,
    onExportSingleTripCsvClick: (TripEntity) -> Unit,
    onExportSingleTripPdfClick: (TripEntity) -> Unit,
    onDeleteTripConfirmed: (String) -> Unit
) {
    val isEn = LocalAppLanguage.current == AppLanguage.EN
    val today = remember { LocalDate.now() }
    var tripPendingDeletion by remember { mutableStateOf<TripEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Barra de acciones superiores en Pestaña Viajes
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isEn) "Trip Management" else "Gestión de Viajes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = OceanNavy
                    )

                    // Fila superior: Exportar Listado General (PDF) y al lado Exportar (CSV)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportAllTripsPdfClick,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, CoralRedDark),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRedDark),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("export_all_trips_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Export General List (PDF)" else "Exportar Listado General (PDF)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onExportAllTripsCsvClick,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, EmeraldGreenDark),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreenDark),
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("export_all_trips_csv_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Export CSV" else "Exportar CSV",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1
                            )
                        }
                    }

                    // Botón de Nuevo Viaje donde estaba Exportar Listado General, un poco más grande manteniendo color
                    Button(
                        onClick = onCreateNewTripClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TravelBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("create_new_trip_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "New Trip" else "Nuevo Viaje",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                        )
                    }
                }
            }
        }

        // Listado de todos los viajes
        items(trips, key = { it.id }) { trip ->
            val isActive = trip.id == activeTripId
            val tParts = allParticipants.filter { it.tripId == trip.id }
            val tExps = allExpenses.filter { it.tripId == trip.id }
            val tIncs = allIncomes.filter { it.tripId == trip.id }
            val spent = tExps.sumOf { it.amountInBaseCurrency }
            val inc = tIncs.sumOf { it.amount }

            val startLocal = runCatching { LocalDate.parse(trip.startDate.take(10)) }.getOrNull()
            val endLocal = runCatching { LocalDate.parse(trip.endDate.take(10)) }.getOrNull()
            val (dateStatusLabel, dateStatusBg, dateStatusColor) = when {
                startLocal != null && today.isBefore(startLocal) -> Triple(
                    if (isEn) "⏳ Upcoming" else "⏳ Por iniciar",
                    Color(0xFFFEF3C7),
                    Color(0xFFB45309)
                )
                endLocal != null && today.isAfter(endLocal) -> Triple(
                    if (isEn) "🏁 Completed" else "🏁 Finalizado",
                    Color(0xFFF1F5F9),
                    Color(0xFF475569)
                )
                else -> Triple(
                    if (isEn) "🟢 Ongoing" else "🟢 En curso",
                    EmeraldGreenLight,
                    EmeraldGreenDark
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTrip(trip.id) }
                    .testTag("trip_card_${trip.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) Color(0xFFF8FAFC) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.5.dp),
                border = BorderStroke(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) TravelBlue else Color(0xFFE2E8F0)
                )
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
                            EditableTripRouteIcon(
                                photoUri = trip.headerPhotoUri,
                                onClick = { onTripIconClick(trip) },
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = trip.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = OceanNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = TravelBlue
                                        ) {
                                            Text(
                                                text = if (isEn) "ACTIVE" else "ACTIVO",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = dateStatusBg
                                    ) {
                                        Text(
                                            text = dateStatusLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = dateStatusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "📅 ${FormatUtils.formatShortDate(trip.startDate)} - ${FormatUtils.formatShortDate(trip.endDate)} · 👥 ${tParts.size} ${if (isEn) "travelers" else "viajeros"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onEditTripClick(trip) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("edit_trip_${trip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (isEn) "Edit trip" else "Editar viaje",
                                    tint = TravelBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { tripPendingDeletion = trip },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("delete_trip_${trip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = if (isEn) "Delete trip" else "Eliminar viaje",
                                    tint = CoralRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Métricas del viaje
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (isEn) "Spent" else "Gastado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(spent, trip.baseCurrency),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                    color = OceanNavy
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0FDF4)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (isEn) "Common Pot" else "Bote Común",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreenDark
                                )
                                Text(
                                    text = "+${FormatUtils.formatCurrency(inc, trip.baseCurrency)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                    color = EmeraldGreenDark
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (isEn) "Budget" else "Presupuesto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TravelBlue
                                )
                                Text(
                                    text = trip.budget?.let { FormatUtils.formatCurrency(it, trip.baseCurrency) }
                                        ?: if (isEn) "Flexible" else "Flexible",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                    color = TravelBlue
                                )
                            }
                        }
                    }

                    if (trip.budget != null && trip.budget > 0.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val progress = (spent / trip.budget).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = if (progress >= 0.9f) CoralRed else TravelBlue,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Botones para exportar los gastos de este viaje a CSV o PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportSingleTripCsvClick(trip) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Export Expenses CSV" else "Exportar Gastos CSV",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = { onExportSingleTripPdfClick(trip) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRedDark),
                            border = BorderStroke(1.dp, CoralRedDark.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "PDF Report" else "Reporte PDF",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    // Diálogo de confirmación seguro para eliminar viaje
    if (tripPendingDeletion != null) {
        val target = tripPendingDeletion!!
        AlertDialog(
            onDismissRequest = { tripPendingDeletion = null },
            title = {
                Text(
                    text = if (isEn) "Delete trip \"${target.title}\"" else "Eliminar viaje \"${target.title}\"",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = if (isEn) {
                        "Are you sure you want to delete this trip and all its expenses, participants and pot contributions? This action cannot be undone."
                    } else {
                        "¿Estás seguro de que deseas eliminar este viaje y todos sus gastos, participantes y aportaciones al bote? Esta acción no se puede deshacer."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTripConfirmed(target.id)
                        tripPendingDeletion = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text(if (isEn) "Yes, delete trip" else "Sí, eliminar viaje", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripPendingDeletion = null }) {
                    Text(if (isEn) "Cancel" else "Cancelar")
                }
            }
        )
    }
}
