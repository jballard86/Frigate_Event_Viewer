package com.example.frigateeventviewer.data.api

import com.example.frigateeventviewer.data.model.EventsResponse
import com.example.frigateeventviewer.data.model.StatsResponse
import com.example.frigateeventviewer.data.model.StatusResponse
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

    @GET("stats")
    suspend fun getStats(): StatsResponse

    @GET("status")
    suspend fun getStatus(): StatusResponse

    /** Marks the event as viewed (POST /viewed/&lt;path:event_path&gt;). Contract §1.6. */
    @POST("viewed/{event_path}")
    suspend fun markViewed(
        @Path("event_path", encoded = true) eventPath: String
    )

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
}
