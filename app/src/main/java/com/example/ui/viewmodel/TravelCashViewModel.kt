package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TravelCashDatabase
import com.example.data.model.AvatarColorPalette
import com.example.data.model.ExpenseEntity
import com.example.data.model.IncomeEntity
import com.example.data.model.ParticipantEntity
import com.example.data.model.TripEntity
import com.example.data.remote.NominatimPlaceSuggestion
import com.example.data.remote.NominatimService
import com.example.data.repository.TravelCashRepository
import com.example.domain.DebtCalculationResult
import com.example.domain.DebtCalculator
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppThemeMode
import com.example.util.CsvColumnMapping
import com.example.util.ExportImportManager
import com.example.util.ParsedCsvSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

enum class MainTab(val titleEs: String, val titleEn: String) {
    EXPENSES("Gastos", "Expenses"),
    MAP("Mapa", "Map"),
    STATS("Estadística", "Stats"),
    SPLITS("Cuentas", "Splits"),
    TRIPS("Viajes", "Trips"),
    SETTINGS("Ajustes", "Settings")
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false
)

data class CsvPendingManualImport(
    val parsedSheet: ParsedCsvSheet,
    val createNewTrip: Boolean,
    val suggestedTripTitle: String
)

data class TravelCashUiState(
    val trips: List<TripEntity> = emptyList(),
    val activeTripId: String? = null,
    val activeTrip: TripEntity? = null,
    val activeParticipants: List<ParticipantEntity> = emptyList(),
    val activeExpenses: List<ExpenseEntity> = emptyList(),
    val activeIncomes: List<IncomeEntity> = emptyList(),
    val allParticipants: List<ParticipantEntity> = emptyList(),
    val allExpenses: List<ExpenseEntity> = emptyList(),
    val allIncomes: List<IncomeEntity> = emptyList(),
    val debtResult: DebtCalculationResult = DebtCalculationResult(
        emptyList(), emptyList(), 0.0, 0.0, 0.0
    ),
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val extraBalance: Double = 0.0,
    val isInitialized: Boolean = false
)

class TravelCashViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TravelCashDatabase.getInstance(application).travelCashDao()
    private val repository = TravelCashRepository(dao)

    private val _selectedTab = MutableStateFlow(MainTab.EXPENSES)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _activeTripId = MutableStateFlow<String?>(null)

    private val _toastMessage = MutableStateFlow<ToastMessage?>(null)
    val toastMessage: StateFlow<ToastMessage?> = _toastMessage.asStateFlow()

    // Filtros en Pestaña Gastos
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow<String?>(null)
    val selectedDateFilter: StateFlow<String?> = _selectedDateFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    // Ubicación GPS y sugerencias Nominatim
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _placeSuggestions = MutableStateFlow<List<NominatimPlaceSuggestion>>(emptyList())
    val placeSuggestions: StateFlow<List<NominatimPlaceSuggestion>> = _placeSuggestions.asStateFlow()

    private val _isSearchingPlaces = MutableStateFlow(false)
    val isSearchingPlaces: StateFlow<Boolean> = _isSearchingPlaces.asStateFlow()

    private var placeSearchJob: Job? = null

    // Estado para mapeo manual de CSV
    private val _pendingCsvManualImport = MutableStateFlow<CsvPendingManualImport?>(null)
    val pendingCsvManualImport: StateFlow<CsvPendingManualImport?> = _pendingCsvManualImport.asStateFlow()

    val uiState: StateFlow<TravelCashUiState> = combine(
        repository.allTripsFlow,
        repository.allParticipantsFlow,
        repository.allExpensesFlow,
        repository.allIncomesFlow,
        _activeTripId
    ) { trips, participants, expenses, incomes, currentActiveId ->
        val resolvedActiveTrip = trips.find { it.id == currentActiveId } ?: trips.firstOrNull()
        val resolvedId = resolvedActiveTrip?.id

        val activeParts = if (resolvedId != null) {
            participants.filter { it.tripId == resolvedId }
        } else emptyList()

        val activeExps = if (resolvedId != null) {
            expenses.filter { it.tripId == resolvedId }
        } else emptyList()

        val activeIncs = if (resolvedId != null) {
            incomes.filter { it.tripId == resolvedId }
        } else emptyList()

        val debtResult = DebtCalculator.calculateDebtBalances(activeParts, activeExps, activeIncs)
        val spent = activeExps.sumOf { it.amountInBaseCurrency }
        val inc = activeIncs.sumOf { it.amount }
        // Saldo Extra: Calculado como Ingresos Totales - Gastado Total (o Presupuesto - Gastado + Ingresos si hay presupuesto fijo)
        val extra = if (resolvedActiveTrip?.budget != null && resolvedActiveTrip.budget > 0.0) {
            resolvedActiveTrip.budget - spent + inc
        } else {
            inc - spent
        }

        TravelCashUiState(
            trips = trips,
            activeTripId = resolvedId,
            activeTrip = resolvedActiveTrip,
            activeParticipants = activeParts,
            activeExpenses = activeExps,
            activeIncomes = activeIncs,
            allParticipants = participants,
            allExpenses = expenses,
            allIncomes = incomes,
            debtResult = debtResult,
            totalSpent = spent,
            totalIncome = inc,
            extraBalance = extra,
            isInitialized = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TravelCashUiState()
    )

    init {
        viewModelScope.launch {
            val seedTripId = repository.ensureSeedDataIfEmpty()
            if (_activeTripId.value == null && seedTripId != null) {
                _activeTripId.value = seedTripId
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun selectActiveTrip(tripId: String) {
        _activeTripId.value = tripId
        clearExpenseFilters()
    }

    fun showToast(message: String, isError: Boolean = false) {
        _toastMessage.value = ToastMessage(text = message, isError = isError)
        viewModelScope.launch {
            val currentId = _toastMessage.value?.id
            delay(3200)
            if (_toastMessage.value?.id == currentId) {
                _toastMessage.value = null
            }
        }
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    // =========================================================================
    // FILTROS DE GASTOS
    // =========================================================================
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateDateFilter(dateIso: String?) {
        _selectedDateFilter.value = dateIso
    }

    fun updateCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = if (_selectedCategoryFilter.value == category) null else category
    }

    fun clearExpenseFilters() {
        _searchQuery.value = ""
        _selectedDateFilter.value = null
        _selectedCategoryFilter.value = null
    }

    // =========================================================================
    // BÚSQUEDA DE LUGARES POR PROXIMIDAD GPS (NOMINATIM + DEBOUNCE 350ms)
    // =========================================================================
    fun refreshDeviceLocation() {
        viewModelScope.launch {
            val coords = NominatimService.fetchCurrentDeviceLocation(getApplication())
            if (coords != null) {
                _userLocation.value = coords
            }
        }
    }

    fun setManualUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
    }

    fun onPlaceQueryChanged(query: String) {
        placeSearchJob?.cancel()
        if (query.trim().length < 2) {
            _placeSuggestions.value = emptyList()
            _isSearchingPlaces.value = false
            return
        }
        placeSearchJob = viewModelScope.launch {
            _isSearchingPlaces.value = true
            delay(350) // Debounce de 350ms
            val coords = _userLocation.value
            val results = NominatimService.searchPlacesByProximity(
                query = query,
                userLat = coords?.first,
                userLon = coords?.second
            )
            _placeSuggestions.value = results
            _isSearchingPlaces.value = false
        }
    }

    fun clearPlaceSuggestions() {
        _placeSuggestions.value = emptyList()
        _isSearchingPlaces.value = false
    }

    // =========================================================================
    // CRUD DE VIAJES
    // =========================================================================
    fun createTrip(
        title: String,
        startDate: String,
        endDate: String,
        budget: Double?,
        baseCurrency: String,
        participantNames: List<String>
    ) {
        viewModelScope.launch {
            val tripId = "trip-${UUID.randomUUID().toString().take(8)}"
            val newTrip = TripEntity(
                id = tripId,
                title = title.trim().ifEmpty { "Nuevo Viaje" },
                startDate = startDate,
                endDate = endDate,
                budget = budget?.takeIf { it > 0.0 },
                baseCurrency = baseCurrency,
                headerPhotoUri = null,
                createdAt = System.currentTimeMillis()
            )
            val cleanNames = participantNames.map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("Viajero 1", "Viajero 2") }

            val parts = cleanNames.mapIndexed { idx, name ->
                ParticipantEntity(
                    id = "part-${UUID.randomUUID().toString().take(8)}",
                    tripId = tripId,
                    name = name,
                    avatarColor = AvatarColorPalette.colors[idx % AvatarColorPalette.colors.size]
                )
            }

            repository.insertTrip(newTrip)
            repository.insertParticipants(parts)
            _activeTripId.value = tripId
            showToast("Viaje \"${newTrip.title}\" creado correctamente")
        }
    }

    fun updateTrip(trip: TripEntity) {
        viewModelScope.launch {
            repository.updateTrip(trip)
            showToast("Viaje actualizado")
        }
    }

    fun updateTripDetails(
        trip: TripEntity,
        title: String,
        startDate: String,
        endDate: String,
        budget: Double?,
        baseCurrency: String,
        participantNames: List<String>
    ) {
        viewModelScope.launch {
            val updatedTrip = trip.copy(
                title = title.trim().ifEmpty { trip.title },
                startDate = startDate,
                endDate = endDate,
                budget = budget?.takeIf { it > 0.0 },
                baseCurrency = baseCurrency
            )
            repository.updateTrip(updatedTrip)

            val cleanNames = participantNames.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleanNames.isNotEmpty()) {
                val existingParts = uiState.value.allParticipants.filter { it.tripId == trip.id }
                val usedExistingIds = mutableSetOf<String>()

                cleanNames.forEachIndexed { idx, name ->
                    val exactMatch = existingParts.find {
                        it.id !in usedExistingIds && it.name.equals(name, ignoreCase = true)
                    }
                    if (exactMatch != null) {
                        usedExistingIds.add(exactMatch.id)
                        if (exactMatch.name != name) {
                            repository.updateParticipant(exactMatch.copy(name = name))
                        }
                    } else {
                        val newPart = ParticipantEntity(
                            id = "part-${UUID.randomUUID().toString().take(8)}",
                            tripId = trip.id,
                            name = name,
                            avatarColor = AvatarColorPalette.colors[(existingParts.size + idx) % AvatarColorPalette.colors.size]
                        )
                        repository.insertParticipant(newPart)
                    }
                }

                // Eliminar participantes que el usuario haya quitado explícitamente de la lista al editar
                existingParts.filter { it.id !in usedExistingIds }.forEach { removed ->
                    repository.deleteParticipant(removed.id)
                }
            }

            val isEn = _language.value == AppLanguage.EN
            showToast(
                if (isEn) "Trip \"${updatedTrip.title}\" updated" else "Viaje \"${updatedTrip.title}\" actualizado"
            )
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            repository.deleteCompleteTrip(tripId)
            val remaining = repository.getAllTripsOnce()
            _activeTripId.value = remaining.firstOrNull()?.id
            showToast("Viaje eliminado")
        }
    }

    // =========================================================================
    // CRUD DE PARTICIPANTES
    // =========================================================================
    fun addParticipant(name: String) {
        val tripId = uiState.value.activeTripId ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val idx = uiState.value.activeParticipants.size
            val newPart = ParticipantEntity(
                id = "part-${UUID.randomUUID().toString().take(8)}",
                tripId = tripId,
                name = clean,
                avatarColor = AvatarColorPalette.colors[idx % AvatarColorPalette.colors.size]
            )
            repository.insertParticipant(newPart)
            showToast("Participante \"$clean\" añadido")
        }
    }

    private val prefs = application.getSharedPreferences("travel_cash_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching {
            AppThemeMode.valueOf(prefs.getString("theme_mode_v2", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name)
        }.getOrDefault(AppThemeMode.LIGHT)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(
        runCatching {
            AppLanguage.valueOf(prefs.getString("app_language", AppLanguage.ES.name) ?: AppLanguage.ES.name)
        }.getOrDefault(AppLanguage.ES)
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _reminderNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean("reminder_notifications_enabled", false)
    )
    val reminderNotificationsEnabled: StateFlow<Boolean> = _reminderNotificationsEnabled.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode_v2", mode.name).apply()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        prefs.edit().putString("app_language", lang.name).apply()
        val msg = if (lang == AppLanguage.EN) "Language changed to English" else "Idioma cambiado a Español"
        showToast(msg)
    }

    fun setReminderNotificationsEnabled(enabled: Boolean) {
        _reminderNotificationsEnabled.value = enabled
        prefs.edit().putBoolean("reminder_notifications_enabled", enabled).apply()
        val isEn = _language.value == AppLanguage.EN
        val app = getApplication<Application>()
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val channelId = "travel_cash_reminders"

        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    if (isEn) "Trip Expense Reminders" else "Recordatorios de Gastos de Viaje",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = if (isEn) {
                        "Reminders to log daily trip expenses and common pot contributions"
                    } else {
                        "Recordatorios para anotar los gastos diarios del viaje y el bote común"
                    }
                }
                nm?.createNotificationChannel(channel)
            }
            try {
                val activeTitle = uiState.value.activeTrip?.title ?: "Travel Cash"
                val notification = androidx.core.app.NotificationCompat.Builder(app, channelId)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(
                        if (isEn) "Travel Cash · Expense Reminder Active" else "Travel Cash · Recordatorio Activado"
                    )
                    .setContentText(
                        if (isEn) {
                            "Don't forget to log today's expenses for \"$activeTitle\"."
                        } else {
                            "No olvides registrar los gastos de hoy en \"$activeTitle\"."
                        }
                    )
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(1001, notification)
            } catch (_: SecurityException) {
            }
            showToast(
                if (isEn) "🔔 Reminder notifications enabled" else "🔔 Notificaciones de recordatorio activadas"
            )
        } else {
            nm?.cancel(1001)
            showToast(
                if (isEn) "🔕 Reminder notifications disabled" else "🔕 Notificaciones de recordatorio desactivadas"
            )
        }
    }

    fun updateParticipantPhoto(participant: ParticipantEntity, newPhotoUri: String?) {
        viewModelScope.launch {
            repository.updateParticipant(participant.copy(photoUri = newPhotoUri))
            val msg = if (newPhotoUri != null) {
                "Foto de ${participant.name} actualizada"
            } else {
                "Icono por defecto restaurado para ${participant.name}"
            }
            showToast(msg)
        }
    }

    fun processPdfImport(pdfBytes: ByteArray) {
        val active = uiState.value.activeTrip ?: return
        viewModelScope.launch {
            val extracted = ExportImportManager.parseExpensesFromPdfBytes(
                pdfBytes = pdfBytes,
                tripId = active.id,
                baseCurrency = active.baseCurrency,
                existingParticipants = uiState.value.activeParticipants
            )
            if (extracted.isNotEmpty()) {
                repository.insertExpenses(extracted)
                showToast("PDF importado: ${extracted.size} gastos añadidos al viaje")
            } else {
                showToast("No se detectaron líneas de gasto con importe en el PDF seleccionado", isError = true)
            }
        }
    }

    fun updateParticipantName(participant: ParticipantEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.updateParticipant(participant.copy(name = clean))
            showToast("Nombre actualizado a \"$clean\"")
        }
    }

    fun deleteParticipant(participant: ParticipantEntity) {
        viewModelScope.launch {
            repository.deleteParticipant(participant.id)
            showToast("Participante \"${participant.name}\" eliminado")
        }
    }

    // =========================================================================
    // CRUD DE GASTOS
    // =========================================================================
    fun saveExpense(expense: ExpenseEntity, isEdit: Boolean) {
        viewModelScope.launch {
            if (isEdit) {
                repository.updateExpense(expense)
                showToast("Gasto actualizado correctamente")
            } else {
                repository.insertExpense(expense)
                showToast("Gasto guardado correctamente")
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            showToast("Gasto eliminado")
        }
    }

    // =========================================================================
    // CRUD DE INGRESOS (BOTE COMÚN)
    // =========================================================================
    fun saveIncome(income: IncomeEntity, isEdit: Boolean) {
        viewModelScope.launch {
            if (isEdit) {
                repository.updateIncome(income)
                showToast("Aportación al bote actualizada")
            } else {
                repository.insertIncome(income)
                showToast("Aportación al bote registrada")
            }
        }
    }

    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            repository.deleteIncome(incomeId)
            showToast("Aportación al bote eliminada")
        }
    }

    // =========================================================================
    // IMPORTACIÓN CSV Y MAPEO MANUAL DE COLUMNAS
    // =========================================================================
    fun processCsvImportContent(
        csvContent: String,
        createNewTrip: Boolean,
        suggestedFileName: String = "Viaje Importado CSV"
    ) {
        val sheet = ExportImportManager.parseCsvContent(csvContent)
        if (sheet.rows.isEmpty()) {
            showToast("El archivo CSV está vacío o no tiene filas válidas", isError = true)
            return
        }

        if (sheet.isStandardAutoDetected) {
            finalizeCsvImport(
                sheet = sheet,
                mapping = sheet.suggestedMapping,
                createNewTrip = createNewTrip,
                tripTitle = suggestedFileName.removeSuffix(".csv")
            )
        } else {
            // Abrir Modal de Mapeo Manual de Columnas CSV
            _pendingCsvManualImport.value = CsvPendingManualImport(
                parsedSheet = sheet,
                createNewTrip = createNewTrip,
                suggestedTripTitle = suggestedFileName.removeSuffix(".csv")
            )
        }
    }

    fun confirmManualCsvMapping(mapping: CsvColumnMapping, customTripTitle: String) {
        val pending = _pendingCsvManualImport.value ?: return
        _pendingCsvManualImport.value = null
        finalizeCsvImport(
            sheet = pending.parsedSheet,
            mapping = mapping,
            createNewTrip = pending.createNewTrip,
            tripTitle = customTripTitle.ifBlank { pending.suggestedTripTitle }
        )
    }

    fun dismissManualCsvMapping() {
        _pendingCsvManualImport.value = null
    }

    private fun finalizeCsvImport(
        sheet: ParsedCsvSheet,
        mapping: CsvColumnMapping,
        createNewTrip: Boolean,
        tripTitle: String
    ) {
        viewModelScope.launch {
            val targetTripId: String
            val baseCurrency: String
            val existingParts: List<ParticipantEntity>

            if (createNewTrip || uiState.value.activeTrip == null) {
                targetTripId = "trip-csv-${UUID.randomUUID().toString().take(8)}"
                baseCurrency = "EUR"
                existingParts = emptyList()
            } else {
                val active = uiState.value.activeTrip!!
                targetTripId = active.id
                baseCurrency = active.baseCurrency
                existingParts = uiState.value.activeParticipants
            }

            val (newParticipants, newExpenses) = ExportImportManager.convertRowsToEntities(
                tripId = targetTripId,
                baseCurrency = baseCurrency,
                existingParticipants = existingParts,
                rows = sheet.rows,
                mapping = mapping
            )

            if (newExpenses.isEmpty()) {
                showToast("No se encontraron importes válidos en las filas seleccionadas", isError = true)
                return@launch
            }

            if (createNewTrip || uiState.value.activeTrip == null) {
                val minDate = newExpenses.minOfOrNull { it.date } ?: LocalDate.now().toString()
                val maxDate = newExpenses.maxOfOrNull { it.date } ?: LocalDate.now().toString()
                val newTrip = TripEntity(
                    id = targetTripId,
                    title = tripTitle.trim().ifEmpty { "Viaje Importado CSV" },
                    startDate = minDate,
                    endDate = maxDate,
                    budget = null,
                    baseCurrency = baseCurrency,
                    headerPhotoUri = null,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertTrip(newTrip)
            }

            if (newParticipants.isNotEmpty()) {
                repository.insertParticipants(newParticipants)
            }
            repository.insertExpenses(newExpenses)
            _activeTripId.value = targetTripId
            showToast("CSV importado: ${newExpenses.size} gastos registrados")
        }
    }

    // =========================================================================
    // BACKUP Y RESTAURACIÓN JSON (ESTADO COMPLETO DE LA APP)
    // =========================================================================
    fun generateFullBackupJson(): String {
        val state = uiState.value
        return ExportImportManager.exportFullBackupJson(
            trips = state.trips,
            participants = state.allParticipants,
            expenses = state.allExpenses,
            incomes = state.allIncomes,
            activeTripId = state.activeTripId,
            themeMode = _themeMode.value.name,
            language = _language.value.name
        )
    }

    fun restoreJsonBackup(jsonContent: String, replaceAll: Boolean) {
        viewModelScope.launch {
            try {
                val payload = ExportImportManager.parseFullBackupJson(jsonContent)
                if (payload.trips.isEmpty()) {
                    showToast("El archivo JSON no contiene viajes válidos para restaurar", isError = true)
                    return@launch
                }
                repository.restoreBackup(
                    trips = payload.trips,
                    participants = payload.participants,
                    expenses = payload.expenses,
                    incomes = payload.incomes,
                    replaceAll = replaceAll
                )

                // Restaurar ajustes de tema e idioma si vienen incluidos en el backup
                payload.themeMode?.let { modeStr ->
                    runCatching { AppThemeMode.valueOf(modeStr) }.getOrNull()?.let { mode ->
                        _themeMode.value = mode
                        prefs.edit().putString("theme_mode", mode.name).apply()
                    }
                }
                payload.language?.let { langStr ->
                    runCatching { AppLanguage.valueOf(langStr) }.getOrNull()?.let { lang ->
                        _language.value = lang
                        prefs.edit().putString("app_language", lang.name).apply()
                    }
                }

                // Seleccionar el viaje activo exportado si existe, o el primer viaje restaurado
                val preferredTripId = payload.activeTripId?.takeIf { id ->
                    payload.trips.any { it.id == id }
                } ?: payload.trips.first().id
                _activeTripId.value = preferredTripId

                val modeText = if (replaceAll) "reemplazando datos" else "fusionando datos"
                showToast(
                    "Backup restaurado ($modeText): ${payload.trips.size} viajes, ${payload.expenses.size} gastos y ${payload.incomes.size} ingresos"
                )
            } catch (e: Exception) {
                showToast("Error al procesar el JSON de copia de seguridad", isError = true)
            }
        }
    }
}
