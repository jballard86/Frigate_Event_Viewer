package com.example.frigateeventviewer.data.api

import com.example.frigateeventviewer.data.model.CamerasResponse
import com.example.frigateeventviewer.data.model.EventsResponse
import com.example.frigateeventviewer.data.model.SnoozeEntry
import com.example.frigateeventviewer.data.model.SnoozeRequest
import com.example.frigateeventviewer.data.model.SnoozeResponse
import com.example.frigateeventviewer.data.model.StatsResponse
import com.example.frigateeventviewer.data.model.StatusResponse
import com.example.frigateeventviewer.data.model.UnreadCountResponse
import com.example.frigateeventviewer.data.model.DailyReviewDatesResponse
import com.example.frigateeventviewer.data.model.DailyReviewResponse
import com.example.frigateeventviewer.data.model.GenerateReportResponse
import com.example.frigateeventviewer.data.model.MarkAllViewedResponse
import com.example.frigateeventviewer.data.model.RegisterDeviceRequest
import com.example.frigateeventviewer.data.model.RegisterDeviceResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service for the Frigate Event Buffer API.
 * All paths are relative to the dynamic base URL (see [ApiClient]).
 * Contract: [docs/MOBILE_API_CONTRACT.md].
 */
interface FrigateApiService {

    @GET("events")
    suspend fun getEvents(
        @Query("filter") filter: String? = null
    ): EventsResponse

    /** List events for one camera (GET /events/&lt;camera&gt;). Contract §1.3. */
    @GET("events/{camera}")
    suspend fun getEventsByCamera(
        @Path("camera", encoded = true) camera: String,
        @Query("filter") filter: String? = null
    ): EventsResponse

    /** List cameras (GET /cameras). Contract §1.1. */
    @GET("cameras")
    suspend fun getCameras(): CamerasResponse

    @GET("stats")
    suspend fun getStats(): StatsResponse

    @GET("status")
    suspend fun getStatus(): StatusResponse

    /** Current daily report (GET /api/daily-review/current). Contract §4.2. Returns 404 if no report for today. */
    @GET("api/daily-review/current")
    suspend fun getCurrentDailyReview(): DailyReviewResponse

    /** Available report dates (GET /api/daily-review/dates). Contract §4.1. Newest first. */
    @GET("api/daily-review/dates")
    suspend fun getDailyReviewDates(): DailyReviewDatesResponse

    /** Report for a calendar day (GET /api/daily-review/&lt;date_str&gt;). Contract §4.3. */
    @GET("api/daily-review/{date_str}")
    suspend fun getDailyReviewByDate(@Path("date_str") dateStr: String): DailyReviewResponse

    /** Trigger report generation (POST /api/daily-review/generate). Contract §4.4. Optional [date] query (YYYY-MM-DD). */
    @POST("api/daily-review/generate")
    suspend fun generateDailyReview(
        @Query("date") date: String? = null,
        @Body body: okhttp3.RequestBody = "{}".toRequestBody(
            "application/json".toMediaType(),
        ),
    ): GenerateReportResponse

    /** Marks the event as viewed (POST /viewed/&lt;path:event_path&gt;). Contract §1.6. */
    @POST("viewed/{event_path}")
    suspend fun markViewed(
        @Path("event_path", encoded = true) eventPath: String
    )

    /** Unmarks viewed (DELETE /viewed/&lt;path:event_path&gt;). Contract §1.7. */
    @DELETE("viewed/{event_path}")
    suspend fun unmarkViewed(
        @Path("event_path", encoded = true) eventPath: String
    )

    /** Marks all events viewed (POST /viewed/all). Contract §1.8. */
    @POST("viewed/all")
    suspend fun markAllViewed(): MarkAllViewedResponse

    /** Moves the event to saved (POST /keep/&lt;path:event_path&gt;). Contract §1.4. */
    @POST("keep/{event_path}")
    suspend fun keepEvent(
        @Path("event_path", encoded = true) eventPath: String
    )

    /** Deletes the event folder (POST /delete/&lt;path:subdir&gt;). Contract §1.5. */
    @POST("delete/{event_path}")
    suspend fun deleteEvent(
        @Path("event_path", encoded = true) eventPath: String
    )

    /** Register FCM device token (POST /api/mobile/register). Contract §6.1. */
    @POST("api/mobile/register")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): RegisterDeviceResponse

    /** List active snoozes (GET /api/snooze). Contract §8.2. Returns camera name to snooze entry. */
    @GET("api/snooze")
    suspend fun getSnoozeList(): Map<String, SnoozeEntry>

    /** Set snooze for a camera (POST /api/snooze/&lt;camera&gt;). Contract §8.1. */
    @POST("api/snooze/{camera}")
    suspend fun setSnooze(
        @Path("camera") camera: String,
        @Body body: SnoozeRequest
    ): SnoozeResponse

    /** Clear snooze for a camera (DELETE /api/snooze/&lt;camera&gt;). Contract §8.3. */
    @DELETE("api/snooze/{camera}")
    suspend fun clearSnooze(@Path("camera") camera: String): RegisterDeviceResponse

    /** Get unread event count (GET /api/events/unread_count). Contract §7.1. */
    @GET("api/events/unread_count")
    suspend fun getUnreadCount(): UnreadCountResponse

    /**
     * List go2rtc streams (GET /api/go2rtc/streams). Frigate API; use HTTP base URL from Frigate IP setting.
     * Response is a JSON object whose top-level keys are stream names (e.g. front_door, back_yard).
     */
    @GET("api/go2rtc/streams")
    suspend fun getGo2RtcStreams(): Map<String, Any?>
}
