package com.example.ui.screens

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.CategoryCatalog
import com.example.data.model.ExpenseEntity
import com.example.data.model.ParticipantEntity
import com.example.data.model.PaymentMethodCatalog
import com.example.data.model.TripEntity
import com.example.domain.DebtCalculator
import com.example.ui.components.TravelLocationPinIcon
import com.example.ui.components.createTravelMapPinDrawable
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OceanNavy
import com.example.ui.theme.TravelBlue
import com.example.util.FormatUtils
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapTabScreen(
    trip: TripEntity,
    participants: List<ParticipantEntity>,
    expenses: List<ExpenseEntity>,
    userLocation: Pair<Double, Double>?,
    onRequestLocationRefresh: () -> Unit,
    onEditExpenseClick: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    val isEn = LocalAppLanguage.current == AppLanguage.EN
    val participantMap = remember(participants) { participants.associateBy { it.id } }

    val geoExpenses = remember(expenses) {
        expenses.filter { it.latitude != null && it.longitude != null }
    }

    // Agrupación de gastos geolocalizados por Categoría canónica
    val categoriesWithExpenses = remember(geoExpenses) {
        val grouped = LinkedHashMap<String, MutableList<ExpenseEntity>>()
        geoExpenses.forEach { exp ->
            val canonicalCatName = CategoryCatalog.getCategory(exp.category).name
            grouped.getOrPut(canonicalCatName) { mutableListOf() }.add(exp)
        }
        grouped.entries.map { it.key to it.value.toList() }
    }

    // Por defecto null para que se muestren todos los gastos geolocalizados en el mapa
    var selectedExpense by remember(geoExpenses) {
        mutableStateOf<ExpenseEntity?>(null)
    }
    var expandedCategoryKey by remember { mutableStateOf<String?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "TravelCashAndroidApp/1.0"
    }

    fun fitAllPins(mapView: MapView?) {
        val map = mapView ?: return
        if (geoExpenses.isEmpty()) {
            val uLoc = userLocation
            if (uLoc != null) {
                map.controller.setZoom(14.0)
                map.controller.animateTo(GeoPoint(uLoc.first, uLoc.second))
            } else {
                map.controller.setZoom(5.0)
                map.controller.animateTo(GeoPoint(40.4168, -3.7038))
            }
            return
        }
        if (geoExpenses.size == 1 && geoExpenses.first().originLatitude == null) {
            val only = geoExpenses.first()
            map.controller.setZoom(15.5)
            map.controller.animateTo(GeoPoint(only.latitude!!, only.longitude!!))
        } else {
            val lats = geoExpenses.flatMap { listOfNotNull(it.latitude, it.originLatitude) }
            val lons = geoExpenses.flatMap { listOfNotNull(it.longitude, it.originLongitude) }
            val north = (lats.maxOrNull() ?: 0.0) + 0.025
            val south = (lats.minOrNull() ?: 0.0) - 0.025
            val east = (lons.maxOrNull() ?: 0.0) + 0.025
            val west = (lons.minOrNull() ?: 0.0) - 0.025
            try {
                val box = BoundingBox(north, east, south, west)
                map.zoomToBoundingBox(box, true, 96)
            } catch (_: Exception) {
                map.controller.setZoom(12.0)
                map.controller.animateTo(GeoPoint(lats.first(), lons.first()))
            }
        }
    }

    fun showAllGeolocatedExpenses() {
        selectedExpense = null
        expandedCategoryKey = null
        fitAllPins(mapViewRef)
    }

    // Construcción de segmentos punto a punto para gastos de Vuelos, Transporte y Taxi
    val transitRouteSegments = remember(geoExpenses) {
        val sortedGeo = geoExpenses.sortedWith(
            compareBy<ExpenseEntity> { it.date }.thenBy { it.createdAt }
        )
        // Cada segmento: Triple(startGeoPoint, endGeoPoint, isFlight)
        val segments = mutableListOf<Triple<GeoPoint, GeoPoint, Boolean>>()
        val seenPairs = mutableSetOf<String>()

        fun addCoordsSegment(fLat: Double, fLon: Double, tLat: Double, tLon: Double, isFlight: Boolean) {
            if (kotlin.math.abs(fLat - tLat) < 0.00001 && kotlin.math.abs(fLon - tLon) < 0.00001) return
            val p1 = String.format(java.util.Locale.US, "%.4f,%.4f", fLat, fLon)
            val p2 = String.format(java.util.Locale.US, "%.4f,%.4f", tLat, tLon)
            val key = listOf(p1, p2).sorted().joinToString("::")
            if (seenPairs.add(key)) {
                segments.add(Triple(GeoPoint(fLat, fLon), GeoPoint(tLat, tLon), isFlight))
            }
        }

        // 1. Si un gasto de Vuelos, Transporte o Taxi tiene Origen y Destino propios, trazar su línea directa Origen -> Destino
        sortedGeo.forEach { exp ->
            val oLat = exp.originLatitude
            val oLon = exp.originLongitude
            val dLat = exp.latitude
            val dLon = exp.longitude
            if (oLat != null && oLon != null && dLat != null && dLon != null) {
                val catName = CategoryCatalog.getCategory(exp.category).name
                val isFlight = catName.equals("Vuelos", ignoreCase = true)
                addCoordsSegment(oLat, oLon, dLat, dLon, isFlight)
            }
        }

        // 2. Conectar cada gasto de Vuelos, Transporte o Taxi con el punto anterior/siguiente de la ruta si no tenía origen propio
        for (i in 0 until sortedGeo.size - 1) {
            val a = sortedGeo[i]
            val b = sortedGeo[i + 1]
            val catA = CategoryCatalog.getCategory(a.category).name
            val catB = CategoryCatalog.getCategory(b.category).name
            val aIsFlight = catA.equals("Vuelos", ignoreCase = true)
            val bIsFlight = catB.equals("Vuelos", ignoreCase = true)
            val aIsTransport = CategoryCatalog.isRouteCategory(catA) && !aIsFlight
            val bIsTransport = CategoryCatalog.isRouteCategory(catB) && !bIsFlight

            val fLat = a.latitude ?: continue
            val fLon = a.longitude ?: continue
            val tLat = b.originLatitude ?: b.latitude ?: continue
            val tLon = b.originLongitude ?: b.longitude ?: continue

            if (aIsFlight || bIsFlight) {
                addCoordsSegment(fLat, fLon, tLat, tLon, isFlight = true)
            } else if (aIsTransport || bIsTransport) {
                addCoordsSegment(fLat, fLon, tLat, tLon, isFlight = false)
            }
        }

        segments.toList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa interactivo osmdroid a pantalla completa
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(12.0)
                    mapViewRef = this
                }
            },
            update = { map ->
                map.overlays.clear()

                // 1. Tap en el fondo del mapa para deseleccionar el punto actual y volver a mostrar todos los gastos geolocalizados
                val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (selectedExpense != null) {
                            selectedExpense = null
                            expandedCategoryKey = null
                            fitAllPins(map)
                            return true
                        }
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
                map.overlays.add(mapEventsOverlay)

                // 2. Líneas directas punto a punto simulando Vuelos, Transporte y Taxi
                transitRouteSegments.forEach { (startPoint, endPoint, isFlight) ->
                    // Halo exterior suave para resaltar la línea sobre cualquier mapa
                    val haloLine = Polyline(map).apply {
                        setPoints(listOf(startPoint, endPoint))
                        outlinePaint.apply {
                            color = if (isFlight) {
                                android.graphics.Color.argb(120, 186, 230, 253) // Celeste claro vuelo
                            } else {
                                android.graphics.Color.argb(120, 153, 246, 228) // Turquesa claro transporte/taxi
                            }
                            strokeWidth = if (isFlight) 13f else 11f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            isAntiAlias = true
                        }
                        infoWindow = null
                    }
                    map.overlays.add(haloLine)

                    // Línea principal de Vuelo (azul cielo discontinua) o Transporte/Taxi (turquesa/azul ruta)
                    val routeLine = Polyline(map).apply {
                        setPoints(listOf(startPoint, endPoint))
                        outlinePaint.apply {
                            color = if (isFlight) {
                                android.graphics.Color.rgb(2, 132, 199) // #0284C7 Vuelos
                            } else {
                                android.graphics.Color.rgb(13, 148, 136) // #0D9488 Transporte/Taxi
                            }
                            strokeWidth = if (isFlight) 7.5f else 6.5f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = if (isFlight) {
                                DashPathEffect(floatArrayOf(22f, 14f), 0f)
                            } else {
                                DashPathEffect(floatArrayOf(14f, 9f), 0f)
                            }
                            isAntiAlias = true
                        }
                        infoWindow = null
                    }
                    map.overlays.add(routeLine)
                }

                // 3. Marcadores de todos los gastos geolocalizados (incluyendo pin de Origen si existe)
                geoExpenses.forEach { exp ->
                    val lat = exp.latitude ?: return@forEach
                    val lon = exp.longitude ?: return@forEach
                    val catInfo = CategoryCatalog.getCategory(exp.category)
                    val isPinSelected = selectedExpense?.id == exp.id
                    val payerName = if (exp.paidById == DebtCalculator.BOTE_COMUN_ID) {
                        if (isEn) "Common Pot" else "Bote Común"
                    } else {
                        participantMap[exp.paidById]?.name ?: if (isEn) "Traveler" else "Viajero"
                    }

                    // Si tiene coordenadas de Origen distintas al Destino, añadir marcador para el punto de Origen
                    val oLat = exp.originLatitude
                    val oLon = exp.originLongitude
                    if (oLat != null && oLon != null &&
                        (kotlin.math.abs(oLat - lat) > 0.00001 || kotlin.math.abs(oLon - lon) > 0.00001)
                    ) {
                        val originMarker = Marker(map).apply {
                            position = GeoPoint(oLat, oLon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = createTravelMapPinDrawable(
                                context = context,
                                pinColor = catInfo.color,
                                isSelected = isPinSelected
                            )
                            title = "${if (isEn) "Origin" else "Origen"}: ${exp.originName.ifBlank { exp.placeName }}"
                            snippet = "${FormatUtils.formatCurrency(exp.amountInBaseCurrency, trip.baseCurrency)} · ${if (isEn) "Paid by" else "Pagó"}: $payerName"
                            setOnMarkerClickListener { _, _ ->
                                if (selectedExpense?.id == exp.id) {
                                    selectedExpense = null
                                    fitAllPins(map)
                                } else {
                                    selectedExpense = exp
                                    map.controller.setZoom(14.5)
                                    map.controller.animateTo(GeoPoint(oLat, oLon))
                                }
                                true
                            }
                        }
                        map.overlays.add(originMarker)
                    }

                    val marker = Marker(map).apply {
                        position = GeoPoint(lat, lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createTravelMapPinDrawable(
                            context = context,
                            pinColor = catInfo.color,
                            isSelected = isPinSelected
                        )
                        title = exp.placeName.ifBlank { CategoryCatalog.displayName(exp.category, isEn) }
                        snippet = "${FormatUtils.formatCurrency(exp.amountInBaseCurrency, trip.baseCurrency)} · ${if (isEn) "Paid by" else "Pagó"}: $payerName"
                        setOnMarkerClickListener { _, _ ->
                            if (selectedExpense?.id == exp.id) {
                                // Si ya estaba seleccionado y se vuelve a pulsar, se deselecciona y muestra todos los gastos geolocalizados
                                selectedExpense = null
                                fitAllPins(map)
                            } else {
                                selectedExpense = exp
                                map.controller.setZoom(16.0)
                                map.controller.animateTo(GeoPoint(lat, lon))
                            }
                            true
                        }
                    }
                    map.overlays.add(marker)
                }

                map.invalidate()
            }
        )

        LaunchedEffect(geoExpenses.size, mapViewRef) {
            if (mapViewRef != null) {
                fitAllPins(mapViewRef)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                mapViewRef?.onDetach()
            }
        }

        // Barra superior del mapa: Contador + Botón a la izquierda de los botones para mostrar todos los gastos geolocalizados
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showAllGeolocatedExpenses() },
                shape = RoundedCornerShape(12.dp),
                color = OceanNavy.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TravelLocationPinIcon(
                        tint = Color(0xFF38BDF8),
                        accentColor = Color(0xFFFBBF24),
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "${geoExpenses.size} geolocated places" else "${geoExpenses.size} lugares geolocalizados",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón a la izquierda de los botones de control para mostrar todos los gastos geolocalizados
                Surface(
                    onClick = { showAllGeolocatedExpenses() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedExpense == null) TravelBlue else Color.White,
                    border = BorderStroke(1.dp, TravelBlue),
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("map_top_show_all_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = if (isEn) "Show all geolocated expenses" else "Mostrar todos los gastos geolocalizados",
                            tint = if (selectedExpense == null) Color.White else TravelBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEn) "All" else "Todos",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (selectedExpense == null) Color.White else OceanNavy
                        )
                    }
                }

                SmallFloatingActionButton(
                    onClick = { showAllGeolocatedExpenses() },
                    containerColor = Color.White,
                    contentColor = OceanNavy
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = if (isEn) "Fit all trip pins" else "Encuadrar todos los pines del viaje"
                    )
                }

                SmallFloatingActionButton(
                    onClick = {
                        onRequestLocationRefresh()
                        userLocation?.let { (lat, lon) ->
                            mapViewRef?.controller?.setZoom(15.5)
                            mapViewRef?.controller?.animateTo(GeoPoint(lat, lon))
                        }
                    },
                    containerColor = TravelBlue,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = if (isEn) "Center on my GPS location" else "Centrar en mi ubicación GPS"
                    )
                }
            }
        }

        // Panel inferior: Tarjeta emergente del gasto seleccionado + Fila horizontal con Botón "Todos" y Botones desplegables por Categoría
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tarjeta emergente al pulsar un pin o elegir un gasto de una categoría
            AnimatedVisibility(visible = selectedExpense != null) {
                selectedExpense?.let { exp ->
                    val catInfo = CategoryCatalog.getCategory(exp.category)
                    val translatedCat = CategoryCatalog.displayName(exp.category, isEn)
                    val translatedMethod = PaymentMethodCatalog.displayName(exp.paymentMethod, isEn)
                    val payerName = if (exp.paidById == DebtCalculator.BOTE_COMUN_ID) {
                        if (isEn) "Common Pot" else "Bote Común"
                    } else {
                        participantMap[exp.paidById]?.name ?: if (isEn) "Traveler" else "Viajero"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        border = BorderStroke(1.5.dp, catInfo.color)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(catInfo.color.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catInfo.icon,
                                    contentDescription = translatedCat,
                                    tint = catInfo.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exp.placeName.ifBlank { translatedCat },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = OceanNavy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (exp.country.isNotBlank()) {
                                    Text(
                                        text = "📍 ${exp.country}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "$translatedCat · ${FormatUtils.formatShortDate(exp.date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    text = "${if (isEn) "Paid by" else "Pagó"}: $payerName ($translatedMethod)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TravelBlue
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = FormatUtils.formatCurrency(exp.amountInBaseCurrency, trip.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    ),
                                    color = OceanNavy
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEditExpenseClick(exp) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = if (isEn) "Edit expense" else "Editar gasto",
                                            tint = TravelBlue,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    // Al cerrar / deseleccionar el punto, vuelve a encuadrar todos los gastos geolocalizados
                                    IconButton(
                                        onClick = { showAllGeolocatedExpenses() },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = if (isEn) "Deselect and show all" else "Deseleccionar y ver todos",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fila horizontal: Botón de "Todos" a la izquierda + cada botón por Categoría desplegable con sus gastos
            if (geoExpenses.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Botón de "Todos" a la izquierda de los botones de categoría
                    item(key = "map_btn_all") {
                        val isAllActive = selectedExpense == null
                        Surface(
                            onClick = { showAllGeolocatedExpenses() },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAllActive) TravelBlue else Color.White.copy(alpha = 0.96f),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (isAllActive) TravelBlue else Color(0xFFCBD5E1)
                            ),
                            shadowElevation = 4.dp,
                            modifier = Modifier.testTag("map_filter_all_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TravelLocationPinIcon(
                                    tint = if (isAllActive) Color.White else TravelBlue,
                                    accentColor = Color(0xFFF59E0B),
                                    size = 18.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEn) "All (${geoExpenses.size})" else "Todos (${geoExpenses.size})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (isAllActive) Color.White else OceanNavy
                                )
                            }
                        }
                    }

                    // 2. Un botón desplegable por cada Categoría con gastos geolocalizados
                    items(categoriesWithExpenses, key = { it.first }) { (canonicalCatName, catExpenses) ->
                        val catInfo = CategoryCatalog.getCategory(canonicalCatName)
                        val translatedCatName = CategoryCatalog.displayName(canonicalCatName, isEn)
                        val isDropdownOpen = expandedCategoryKey == canonicalCatName
                        val hasSelectedInCat = selectedExpense != null &&
                                CategoryCatalog.getCategory(selectedExpense!!.category).name == canonicalCatName

                        Box {
                            Surface(
                                onClick = {
                                    expandedCategoryKey = if (isDropdownOpen) null else canonicalCatName
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (hasSelectedInCat) OceanNavy else Color.White.copy(alpha = 0.96f),
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = if (hasSelectedInCat || isDropdownOpen) catInfo.color else Color(0xFFCBD5E1)
                                ),
                                shadowElevation = 4.dp,
                                modifier = Modifier.testTag("map_category_dropdown_$canonicalCatName")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(catInfo.color.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catInfo.icon,
                                            contentDescription = translatedCatName,
                                            tint = catInfo.color,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$translatedCatName (${catExpenses.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (hasSelectedInCat) Color.White else OceanNavy
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isDropdownOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = if (hasSelectedInCat) Color.White else Color(0xFF475569),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Menú desplegable con todos los gastos geolocalizados de esa categoría
                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(
                                    surface = Color.White,
                                    onSurface = Color(0xFF334155)
                                )
                            ) {
                                DropdownMenu(
                                    expanded = isDropdownOpen,
                                    onDismissRequest = { expandedCategoryKey = null },
                                    modifier = Modifier
                                        .background(Color.White)
                                        .heightIn(max = 320.dp)
                                ) {
                                    // Cabecera de la categoría en el desplegable
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "📌 $translatedCatName (${catExpenses.size})",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                                color = catInfo.color
                                            )
                                        },
                                        onClick = {
                                            expandedCategoryKey = null
                                        }
                                    )
                                    HorizontalDivider(color = Color(0xFFE2E8F0))

                                    catExpenses.forEach { exp ->
                                        val isExpSelected = selectedExpense?.id == exp.id
                                        val itemTitle = exp.placeName.ifBlank {
                                            CategoryCatalog.displayName(exp.category, isEn)
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = itemTitle,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = if (isExpSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                                            ),
                                                            color = if (isExpSelected) TravelBlue else OceanNavy,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${FormatUtils.formatShortDate(exp.date)} · ${FormatUtils.formatCurrency(exp.amountInBaseCurrency, trip.baseCurrency)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                    if (isExpSelected) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = TravelBlue,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                expandedCategoryKey = null
                                                if (isExpSelected) {
                                                    // Al deseleccionar un punto, muestra el mapa con todos los gastos geolocalizados
                                                    showAllGeolocatedExpenses()
                                                } else {
                                                    selectedExpense = exp
                                                    val lat = exp.latitude
                                                    val lon = exp.longitude
                                                    if (lat != null && lon != null) {
                                                        mapViewRef?.controller?.setZoom(16.0)
                                                        mapViewRef?.controller?.animateTo(GeoPoint(lat, lon))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
