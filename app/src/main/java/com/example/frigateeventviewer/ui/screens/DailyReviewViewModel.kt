package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * UI state for the Daily Review screen (GET /api/daily-review/current).
 */
sealed class DailyReviewState {
    data object Idle : DailyReviewState()
    data object Loading : DailyReviewState()
    data class Success(val markdownText: String) : DailyReviewState()
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
        val json = JsonParser().parse(body).getAsJsonObject()
        val error = json.get("error")?.takeIf { it.isJsonPrimitive }?.getAsString()?.trim()
        if (!error.isNullOrEmpty()) error else exception.message() ?: fallback
    } catch (_: Exception) {
        exception.message() ?: fallback
    }
}

/**
 * ViewModel for the Daily Review screen.
 * Fetches the current daily report (markdown) and can trigger report generation.
 * Exposes [DailyReviewState]; on 404/503 uses the backend JSON error body when present.
 */
class DailyReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)

    private val _state = MutableStateFlow<DailyReviewState>(DailyReviewState.Loading)
    val state: StateFlow<DailyReviewState> = _state.asStateFlow()

    init {
        fetchDailyReview()
    }

    /**
     * Fetches the current daily report (GET /api/daily-review/current).
     * On HTTP errors (e.g. 404, 503), uses the backend JSON "error" field when present.
     */
    fun fetchDailyReview() {
        viewModelScope.launch {
            _state.value = DailyReviewState.Loading
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                val response = service.getCurrentDailyReview()
                _state.value = DailyReviewState.Success(response.summary)
            } catch (e: HttpException) {
                _state.value = DailyReviewState.Error(parseErrorFromBody(e, "Failed to load report"))
            } catch (e: Exception) {
                _state.value = DailyReviewState.Error(
                    e.message ?: "Failed to load report"
                )
            }
        }
    }

    /**
     * Triggers report generation (POST /api/daily-review/generate), then refetches on success.
     */
    fun generateNewReview() {
        viewModelScope.launch {
            _state.value = DailyReviewState.Loading
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = DailyReviewState.Error("No server URL")
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                service.generateDailyReview()
                fetchDailyReview()
            } catch (e: HttpException) {
                _state.value = DailyReviewState.Error(parseErrorFromBody(e, "Failed to generate report"))
            } catch (e: Exception) {
                _state.value = DailyReviewState.Error(
                    e.message ?: "Failed to generate report"
                )
            }
        }
    }
}

/**
 * Factory for [DailyReviewViewModel] so it receives [Application].
 */
class DailyReviewViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyReviewViewModel::class.java)) {
            return DailyReviewViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
