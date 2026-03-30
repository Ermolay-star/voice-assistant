package com.voiceassistant

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)
data class AskRequest(val messages: List<ChatMessage>)
data class AskResponse(val answer: String)

interface AssistantApi {
    @POST("ask")
    suspend fun ask(@Body req: AskRequest): AskResponse
}

object BackendApiClient {
    // Эмулятор: 10.0.2.2 | Реальный телефон: IP компьютера в сети
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val api: AssistantApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AssistantApi::class.java)
    }
}
