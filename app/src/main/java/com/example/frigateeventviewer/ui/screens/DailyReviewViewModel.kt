package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.api.FrigateApiService
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.ZoneId

/**
 * UI state for the Daily Review screen (markdown report for a selected calendar day).
 */
sealed class DailyReviewState {
    data object Idle : DailyReviewState()

    data object Loading : DailyReviewState()

    /**
     * @param markdownText Report body (markdown).
     * @param selectedDateIso Selected day as YYYY-MM-DD.
     * @param isViewingToday True when [selectedDateIso] is the local calendar today (for in-progress chip).
     */
    data class Success(
        val markdownText: String,
        val selectedDateIso: String,
        val isViewingToday: Boolean,
    ) : DailyReviewState()

    /**
     * No reports exist on the server yet (dates list empty and no current report).
     */
    data class Empty(
        val message: String,
    ) : DailyReviewState()

    data class Error(val message: String) : DailyReviewState()
}

/**
 * Parses the backend error message from an HTTP error body.
 * Expects JSON of the form {"error": "Actual error message"}.
 * Falls back to [exception].message() or [fallback] if parsing fails.
 */
private fun parseErrorFromBody(exception: HttpException, fallback: String): String {
    val body = exception.response()?.errorBody()?.string() ?: return exception.message() ?: fallback
    if (body.isBlank()) return exception.message() ?: fallback
    return try {
        val json = JsonParser().parse(body).asJsonObject
        val error = json.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        if (!error.isNullOrEmpty()) error else exception.message() ?: fallback
    } catch (_: Exception) {
        exception.message() ?: fallback
    }
}

private fun todayIso(zoneId: ZoneId = ZoneId.systemDefault()): String =
    LocalDate.now(zoneId).toString()

/**
 * ViewModel for the Daily Review screen.
 * Loads the report for a selected day (defaults to today, or the most recent available report if today has none).
 * Persists selected date in [SavedStateHandle] across configuration changes.
 */
class DailyReviewViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)
    private var lastFetchTime = 0L
    private var lastFetchedDateIso: String? = null

    private val _datesLoading = MutableStateFlow(true)
    val datesLoading: StateFlow<Boolean> = _datesLoading.asStateFlow()

    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()

    private val _selectedDateIso = MutableStateFlow<String?>(savedStateHandle.get(KEY_SELECTED_DATE))
    val selectedDateIso: StateFlow<String?> = _selectedDateIso.asStateFlow()

    private val _state = MutableStateFlow<DailyReviewState>(DailyReviewState.Loading)
    val state: StateFlow<DailyReviewState> = _state.asStateFlow()

    init {
        loadInitial()
    }

    /**
     * Fetches date list, picks default day (saved selection, else today if a report exists, else most recent),
     * then loads markdown for that day.
     */
    fun loadInitial() {
        viewModelScope.launch {
            _datesLoading.value = true
            _state.value = DailyReviewState.Loading
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _datesLoading.value = false
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            val service = ApiClient.createService(baseUrl)
            val datesFromApi = try {
                service.getDailyReviewDates().dates
            } catch (_: Exception) {
                emptyList()
            }
            _availableDates.value = datesFromApi
            _datesLoading.value = false

            val today = todayIso()
            val saved = savedStateHandle.get<String>(KEY_SELECTED_DATE)
            val savedOk = saved != null && (saved == today || datesFromApi.contains(saved))

            val defaultDate: String? = when {
                savedOk -> saved!!
                else -> {
                    val currentOk = try {
                        service.getCurrentDailyReview()
                        true
                    } catch (e: HttpException) {
                        if (e.code() == 404) false else {
                            _state.value = DailyReviewState.Error(parseErrorFromBody(e, "Failed to load report"))
                            return@launch
                        }
                    } catch (e: Exception) {
                        _state.value = DailyReviewState.Error(e.message ?: "Failed to load report")
                        return@launch
                    }
                    when {
                        currentOk -> today
                        datesFromApi.isNotEmpty() -> datesFromApi.first()
                        else -> null
                    }
                }
            }

            if (defaultDate == null) {
                _state.value = DailyReviewState.Empty(
                    message = "No reports have been generated yet. Reports are created automatically at the end of each day, or you can generate one now.",
                )
                return@launch
            }

            _selectedDateIso.value = defaultDate
            savedStateHandle[KEY_SELECTED_DATE] = defaultDate
            fetchReportForDateInternal(service, defaultDate)
        }
    }

    /**
     * Loads markdown for [dateIso] (YYYY-MM-DD) and updates [state].
     */
    fun selectDate(dateIso: String) {
        viewModelScope.launch {
            _state.value = DailyReviewState.Loading
            _selectedDateIso.value = dateIso
            savedStateHandle[KEY_SELECTED_DATE] = dateIso
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            val service = ApiClient.createService(baseUrl)
            fetchReportForDateInternal(service, dateIso)
        }
    }

    /**
     * Re-fetches the report for the current selection without regenerating.
     */
    fun fetchDailyReview() {
        val date = _selectedDateIso.value ?: run {
            loadInitial()
            return
        }
        viewModelScope.launch {
            _state.value = DailyReviewState.Loading
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            val service = ApiClient.createService(baseUrl)
            fetchReportForDateInternal(service, date)
        }
    }

    /**
     * Re-fetches dates and the current report. Used for pull-to-refresh and tab/resume.
     */
    fun refresh(force: Boolean = false) {
        val current = _state.value
        val date = _selectedDateIso.value
        if (!force && current is DailyReviewState.Success && date != null &&
            lastFetchedDateIso == date &&
            lastFetchTime > 0 &&
            System.currentTimeMillis() - lastFetchTime < 5 * 60 * 1000
        ) {
            return
        }
        viewModelScope.launch {
            _datesLoading.value = true
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _datesLoading.value = false
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            val service = ApiClient.createService(baseUrl)
            val datesFromApi = try {
                service.getDailyReviewDates().dates
            } catch (_: Exception) {
                _availableDates.value
            }
            _availableDates.value = datesFromApi
            _datesLoading.value = false

            val targetDate = date ?: run {
                val today = todayIso()
                try {
                    service.getCurrentDailyReview()
                    today
                } catch (e: HttpException) {
                    if (e.code() == 404) {
                        datesFromApi.firstOrNull()
                    } else {
                        _state.value = DailyReviewState.Error(parseErrorFromBody(e, "Failed to load report"))
                        return@launch
                    }
                } catch (e: Exception) {
                    _state.value = DailyReviewState.Error(e.message ?: "Failed to load report")
                    return@launch
                }
            }

            if (targetDate == null) {
                _state.value = DailyReviewState.Empty(
                    message = "No reports have been generated yet. Reports are created automatically at the end of each day, or you can generate one now.",
                )
                return@launch
            }

            _selectedDateIso.value = targetDate
            savedStateHandle[KEY_SELECTED_DATE] = targetDate
            fetchReportForDateInternal(service, targetDate)
        }
    }

    /**
     * Triggers report generation for the [selected date][selectedDateIso], then refetches.
     * When no day is selected (e.g. empty state), uses today's date.
     */
    fun generateNewReview() {
        val dateIso = _selectedDateIso.value ?: todayIso()
        viewModelScope.launch {
            _state.value = DailyReviewState.Loading
            _selectedDateIso.value = dateIso
            savedStateHandle[KEY_SELECTED_DATE] = dateIso
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                service.generateDailyReview(date = dateIso)
                fetchReportForDateInternal(service, dateIso)
                val refreshedDates = try {
                    service.getDailyReviewDates().dates
                } catch (_: Exception) {
                    _availableDates.value
                }
                _availableDates.value = refreshedDates
            } catch (e: HttpException) {
                _state.value = DailyReviewState.Error(parseErrorFromBody(e, "Failed to generate report"))
            } catch (e: Exception) {
                _state.value = DailyReviewState.Error(
                    e.message ?: "Failed to generate report",
                )
            }
        }
    }

    private suspend fun fetchReportForDateInternal(
        service: FrigateApiService,
        dateIso: String,
    ) {
        val today = todayIso()
        try {
            val summary = if (dateIso == today) {
                try {
                    service.getCurrentDailyReview().summary
                } catch (e: HttpException) {
                    if (e.code() == 404) {
                        service.getDailyReviewByDate(dateIso).summary
                    } else {
                        throw e
                    }
                }
            } else {
                service.getDailyReviewByDate(dateIso).summary
            }
            ensureDateInList(dateIso)
            _state.value = DailyReviewState.Success(
                markdownText = summary,
                selectedDateIso = dateIso,
                isViewingToday = dateIso == today,
            )
            lastFetchTime = System.currentTimeMillis()
            lastFetchedDateIso = dateIso
        } catch (e: HttpException) {
            _state.value = DailyReviewState.Error(parseErrorFromBody(e, "Failed to load report"))
        } catch (e: Exception) {
            _state.value = DailyReviewState.Error(e.message ?: "Failed to load report")
        }
    }

    /**
     * Keeps the dropdown in sync when the server returns a report for a day not yet listed (e.g. after first generate).
     */
    private fun ensureDateInList(dateIso: String) {
        val list = _availableDates.value
        if (dateIso in list) return
        _availableDates.value = (listOf(dateIso) + list).distinct().sortedDescending()
    }

    companion object {
        private const val KEY_SELECTED_DATE = "daily_review_selected_date_iso"
    }
}

/**
 * Factory for [DailyReviewViewModel]: supplies [Application] and activity [SavedStateHandle] via [CreationExtras].
 */
class DailyReviewViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(DailyReviewViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
        val savedStateHandle = extras.createSavedStateHandle()
        return DailyReviewViewModel(application, savedStateHandle) as T
    }
}
