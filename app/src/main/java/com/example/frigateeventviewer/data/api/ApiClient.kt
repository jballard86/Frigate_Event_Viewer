package com.example.frigateeventviewer.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory that builds a [FrigateApiService] for a given base URL.
 * Use this whenever the base URL is known (e.g. after loading from DataStore).
 * Retrofit requires the base URL to end with a trailing slash.
 *
 * @param baseUrl Full base URL including scheme (e.g. "http://192.168.1.50:5000/")
 */
object ApiClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder().create()

    /**
     * Creates a new [FrigateApiService] for the given [baseUrl].
     * [baseUrl] must end with '/' (use [SettingsPreferences.normalizeBaseUrl]).
     */
    fun createService(baseUrl: String): FrigateApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return retrofit.create(FrigateApiService::class.java)
    }
}
