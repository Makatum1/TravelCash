package com.example.data.repository

import com.example.data.local.TravelCashDao
import com.example.data.model.AvatarColorPalette
import com.example.data.model.ExpenseEntity
import com.example.data.model.ExpenseSplit
import com.example.data.model.IncomeContribution
import com.example.data.model.IncomeEntity
import com.example.data.model.ParticipantEntity
import com.example.data.model.TripEntity
import kotlinx.coroutines.flow.Flow

class TravelCashRepository(private val dao: TravelCashDao) {

    val allTripsFlow: Flow<List<TripEntity>> = dao.getAllTripsFlow()
    val allParticipantsFlow: Flow<List<ParticipantEntity>> = dao.getAllParticipantsFlow()
    val allExpensesFlow: Flow<List<ExpenseEntity>> = dao.getAllExpensesFlow()
    val allIncomesFlow: Flow<List<IncomeEntity>> = dao.getAllIncomesFlow()

    fun getParticipantsForTrip(tripId: String): Flow<List<ParticipantEntity>> =
        dao.getParticipantsForTripFlow(tripId)

    fun getExpensesForTrip(tripId: String): Flow<List<ExpenseEntity>> =
        dao.getExpensesForTripFlow(tripId)

    fun getIncomesForTrip(tripId: String): Flow<List<IncomeEntity>> =
        dao.getIncomesForTripFlow(tripId)

    suspend fun insertTrip(trip: TripEntity) = dao.insertTrip(trip)
    suspend fun updateTrip(trip: TripEntity) = dao.updateTrip(trip)
    suspend fun deleteCompleteTrip(tripId: String) = dao.deleteCompleteTrip(tripId)

    suspend fun insertParticipant(participant: ParticipantEntity) = dao.insertParticipant(participant)
    suspend fun insertParticipants(participants: List<ParticipantEntity>) = dao.insertParticipants(participants)
    suspend fun updateParticipant(participant: ParticipantEntity) = dao.updateParticipant(participant)
    suspend fun deleteParticipant(participantId: String) = dao.deleteParticipantById(participantId)

    suspend fun insertExpense(expense: ExpenseEntity) = dao.insertExpense(expense)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>) = dao.insertExpenses(expenses)
    suspend fun updateExpense(expense: ExpenseEntity) = dao.updateExpense(expense)
    suspend fun deleteExpense(expenseId: String) = dao.deleteExpenseById(expenseId)

    suspend fun insertIncome(income: IncomeEntity) = dao.insertIncome(income)
    suspend fun updateIncome(income: IncomeEntity) = dao.updateIncome(income)
    suspend fun deleteIncome(incomeId: String) = dao.deleteIncomeById(incomeId)

    suspend fun getAllTripsOnce(): List<TripEntity> = dao.getAllTripsOnce()
    suspend fun getAllParticipantsOnce(): List<ParticipantEntity> = dao.getAllParticipantsOnce()
    suspend fun getAllExpensesOnce(): List<ExpenseEntity> = dao.getAllExpensesOnce()
    suspend fun getAllIncomesOnce(): List<IncomeEntity> = dao.getAllIncomesOnce()

    suspend fun restoreBackup(
        trips: List<TripEntity>,
        participants: List<ParticipantEntity>,
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        replaceAll: Boolean
    ) {
        if (replaceAll) {
            dao.replaceAllBackupData(
                trips = trips,
                participants = participants,
                expenses = expenses,
                incomes = incomes
            )
        } else {
            // Modo FUSIONAR: Conserva todos los viajes, participantes, gastos e ingresos actuales
            // y evita duplicar participantes con el mismo nombre dentro de un mismo viaje
            val existingTrips = dao.getAllTripsOnce().associateBy { it.id }
            val existingParticipants = dao.getAllParticipantsOnce()

            val participantIdRemap = mutableMapOf<String, String>()
            val participantsToInsert = mutableListOf<ParticipantEntity>()

            participants.forEach { incomingPart ->
                val duplicateInSameTrip = existingParticipants.find {
                    it.tripId == incomingPart.tripId &&
                            it.name.trim().equals(incomingPart.name.trim(), ignoreCase = true)
                }
                if (duplicateInSameTrip != null) {
                    participantIdRemap[incomingPart.id] = duplicateInSameTrip.id
                    // Si el participante entrante trae foto y el actual no tenía, actualizamos su foto
                    if (!incomingPart.photoUri.isNullOrBlank() && duplicateInSameTrip.photoUri.isNullOrBlank()) {
                        participantsToInsert.add(duplicateInSameTrip.copy(photoUri = incomingPart.photoUri))
                    }
                } else {
                    participantIdRemap[incomingPart.id] = incomingPart.id
                    participantsToInsert.add(incomingPart)
                }
            }

            val mergedTrips = trips.map { incomingTrip ->
                val existing = existingTrips[incomingTrip.id]
                if (existing != null && incomingTrip.headerPhotoUri.isNullOrBlank() && !existing.headerPhotoUri.isNullOrBlank()) {
                    incomingTrip.copy(headerPhotoUri = existing.headerPhotoUri)
                } else {
                    incomingTrip
                }
            }

            val remappedExpenses = expenses.map { exp ->
                val newPaidBy = participantIdRemap[exp.paidById] ?: exp.paidById
                val newSplits = exp.splits.map { split ->
                    split.copy(participantId = participantIdRemap[split.participantId] ?: split.participantId)
                }
                exp.copy(paidById = newPaidBy, splits = newSplits)
            }

            val remappedIncomes = incomes.map { inc ->
                val newContribs = inc.contributions.map { c ->
                    c.copy(participantId = participantIdRemap[c.participantId] ?: c.participantId)
                }
                inc.copy(contributions = newContribs)
            }

            dao.upsertBackupData(
                trips = mergedTrips,
                participants = participantsToInsert,
                expenses = remappedExpenses,
                incomes = remappedIncomes
            )
        }
    }

    /**
     * Si la base de datos arranca vacía la primera vez, inicializa un viaje de ejemplo completo
     * con 4 participantes, varios gastos en distintas categorías con coordenadas reales,
     * y una aportación al bote común.
     */
    suspend fun ensureSeedDataIfEmpty(): String? {
        val existingTrips = dao.getAllTripsOnce()
        if (existingTrips.isNotEmpty()) return existingTrips.first().id

        val tripId = "trip-japon-2026"
        val demoTrip = TripEntity(
            id = tripId,
            title = "Ruta por Japón y Kioto",
            startDate = "2026-05-10",
            endDate = "2026-05-24",
            budget = 2400.0,
            baseCurrency = "EUR",
            headerPhotoUri = null,
            createdAt = System.currentTimeMillis()
        )

        val p1 = ParticipantEntity("p-carlos", tripId, "Carlos", AvatarColorPalette.colors[0])
        val p2 = ParticipantEntity("p-elena", tripId, "Elena", AvatarColorPalette.colors[1])
        val p3 = ParticipantEntity("p-mateo", tripId, "Mateo", AvatarColorPalette.colors[2])
        val p4 = ParticipantEntity("p-sofia", tripId, "Sofía", AvatarColorPalette.colors[3])
        val participants = listOf(p1, p2, p3, p4)

        fun equalSplits(expId: String, totalEur: Double, partIds: List<String> = participants.map { it.id }): List<ExpenseSplit> {
            val count = partIds.size
            val perPerson = kotlin.math.round((totalEur / count) * 100.0) / 100.0
            val pct = kotlin.math.round((100.0 / count) * 100.0) / 100.0
            return partIds.mapIndexed { index, pid ->
                val amt = if (index == count - 1) {
                    kotlin.math.round((totalEur - perPerson * (count - 1)) * 100.0) / 100.0
                } else perPerson
                ExpenseSplit(
                    id = "$expId-split-$index",
                    expenseId = expId,
                    participantId = pid,
                    amount = amt,
                    percentage = pct
                )
            }
        }

        val expenses = listOf(
            ExpenseEntity(
                id = "exp-ryokan-kioto",
                tripId = tripId,
                amount = 480.0,
                currency = "EUR",
                amountInBaseCurrency = 480.0,
                exchangeRate = 1.0,
                category = "Alojamiento",
                date = "2026-05-15",
                paymentMethod = "Tarjeta",
                notes = "3 noches en Ryokan tradicional con onsen en Gion",
                latitude = 35.0037,
                longitude = 135.7785,
                placeName = "Gion Hatanaka Ryokan, Kioto",
                country = "Japón",
                paidById = p1.id,
                photos = emptyList(),
                splits = equalSplits("exp-ryokan-kioto", 480.0),
                prorationDays = 3,
                createdAt = System.currentTimeMillis() - 400_000L
            ),
            ExpenseEntity(
                id = "exp-shinkansen",
                tripId = tripId,
                amount = 56000.0,
                currency = "JPY",
                amountInBaseCurrency = 341.60,
                exchangeRate = 0.0061,
                category = "Transporte",
                date = "2026-05-15",
                paymentMethod = "Tarjeta",
                notes = "Billetes tren bala Nozomi Tokio - Kioto para los 4",
                latitude = 35.6812,
                longitude = 139.7671,
                placeName = "Estación de Tokio (Shinkansen)",
                country = "Japón",
                paidById = p2.id,
                photos = emptyList(),
                splits = equalSplits("exp-shinkansen", 341.60),
                prorationDays = null,
                createdAt = System.currentTimeMillis() - 350_000L
            ),
            ExpenseEntity(
                id = "exp-ichiran-ramen",
                tripId = tripId,
                amount = 9800.0,
                currency = "JPY",
                amountInBaseCurrency = 59.78,
                exchangeRate = 0.0061,
                category = "Restaurantes y Comida",
                date = "2026-05-16",
                paymentMethod = "Efectivo",
                notes = "Cena de ramen tonkotsu y gyozas en Shibuya",
                latitude = 35.6595,
                longitude = 139.7004,
                placeName = "Ichiran Ramen Shibuya",
                country = "Japón",
                paidById = p3.id,
                photos = emptyList(),
                splits = equalSplits("exp-ichiran-ramen", 59.78),
                prorationDays = null,
                createdAt = System.currentTimeMillis() - 300_000L
            ),
            ExpenseEntity(
                id = "exp-teamlab-planets",
                tripId = tripId,
                amount = 15200.0,
                currency = "JPY",
                amountInBaseCurrency = 92.72,
                exchangeRate = 0.0061,
                category = "Entradas y Ocio",
                date = "2026-05-16",
                paymentMethod = "Tarjeta",
                notes = "Entradas museo inmersivo digital Toyosu",
                latitude = 35.6491,
                longitude = 139.7898,
                placeName = "teamLab Planets TOKYO",
                country = "Japón",
                paidById = p1.id,
                photos = emptyList(),
                splits = equalSplits("exp-teamlab-planets", 92.72),
                prorationDays = null,
                createdAt = System.currentTimeMillis() - 250_000L
            ),
            ExpenseEntity(
                id = "exp-arabica-coffee",
                tripId = tripId,
                amount = 3600.0,
                currency = "JPY",
                amountInBaseCurrency = 21.96,
                exchangeRate = 0.0061,
                category = "Cafés y Snacks",
                date = "2026-05-17",
                paymentMethod = "Efectivo",
                notes = "Matcha lattes y cafés frente al río en Arashiyama",
                latitude = 35.0138,
                longitude = 135.6767,
                placeName = "% Arabica Kyoto Arashiyama",
                country = "Japón",
                paidById = p4.id,
                photos = emptyList(),
                splits = equalSplits("exp-arabica-coffee", 21.96),
                prorationDays = null,
                createdAt = System.currentTimeMillis() - 200_000L
            ),
            ExpenseEntity(
                id = "exp-don-quijote",
                tripId = tripId,
                amount = 14500.0,
                currency = "JPY",
                amountInBaseCurrency = 88.45,
                exchangeRate = 0.0061,
                category = "Compras y Supermercado",
                date = "2026-05-17",
                paymentMethod = "Tarjeta",
                notes = "Snacks japoneses, adaptadores y recuerdos en Shinjuku",
                latitude = 35.6938,
                longitude = 139.7034,
                placeName = "Mega Don Quijote Shinjuku",
                country = "Japón",
                paidById = p2.id,
                photos = emptyList(),
                splits = equalSplits("exp-don-quijote", 88.45),
                prorationDays = null,
                createdAt = System.currentTimeMillis() - 100_000L
            )
        )

        val initialIncome = IncomeEntity(
            id = "inc-bote-inicial",
            tripId = tripId,
            amount = 400.0,
            date = "2026-05-10",
            description = "Fondo inicial Bote Común aeropuerto",
            basePerPerson = 100.0,
            calculationMethod = "perPerson",
            contributions = listOf(
                IncomeContribution(p1.id, 100.0, 0.0),
                IncomeContribution(p2.id, 100.0, 0.0),
                IncomeContribution(p3.id, 100.0, 0.0),
                IncomeContribution(p4.id, 100.0, 0.0)
            )
        )

        dao.insertTrip(demoTrip)
        dao.insertParticipants(participants)
        dao.insertExpenses(expenses)
        dao.insertIncome(initialIncome)

        return tripId
    }
}
