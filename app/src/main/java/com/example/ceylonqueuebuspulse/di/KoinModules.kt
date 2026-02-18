package com.example.ceylonqueuebuspulse.di

import com.example.ceylonqueuebuspulse.data.auth.AuthApi
import com.example.ceylonqueuebuspulse.data.auth.AuthRepository
import com.example.ceylonqueuebuspulse.data.auth.TokenStore
import com.example.ceylonqueuebuspulse.data.local.AppDatabase
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.network.model.MongoApi
import com.example.ceylonqueuebuspulse.data.network.DebugApi
import com.example.ceylonqueuebuspulse.data.network.TomTomSearchApi
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import com.example.ceylonqueuebuspulse.settings.SettingsRepository
import com.example.ceylonqueuebuspulse.settings.SettingsViewModel
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel
import com.example.ceylonqueuebuspulse.traffic.MapComposeViewModel
import com.example.ceylonqueuebuspulse.traffic.LocationTrafficViewModel
import com.example.ceylonqueuebuspulse.util.ConnectivityMonitor
import com.example.ceylonqueuebuspulse.util.RetryPolicy
import com.example.ceylonqueuebuspulse.work.AggregationPlannerWorker
import com.example.ceylonqueuebuspulse.work.MongoAggregationSyncWorker
import com.example.ceylonqueuebuspulse.work.SevereTrafficAlertWorker
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Central Koin module definitions with error handling and retry logic support.
 */
val appModule = module {
    // --- Networking & Connectivity ---
    single { TokenStore(androidContext()) }

    // Auth API must work even when we don't yet have a token.
    single(named("auth")) { RetrofitProvider.mongoRetrofit() }
    single<AuthApi> { get<retrofit2.Retrofit>(named("auth")).create(AuthApi::class.java) }
    single { AuthRepository(api = get(), tokenStore = get(), context = androidContext()) }

    // Main API uses tokens + auto-refresh
    single(named("mongo")) { RetrofitProvider.mongoRetrofit(tokenStore = get(), authRepository = get()) }
    single<MongoApi> { get<retrofit2.Retrofit>(named("mongo")).create(MongoApi::class.java) }

    // Debug / helper API (uses same base URL)
    single<DebugApi> { get<retrofit2.Retrofit>(named("mongo")).create(DebugApi::class.java) }

    // TomTom Retrofit instance (uses TomTom's base url)
    single(named("tomtom")) {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.tomtom.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    single<TomTomSearchApi> { get<Retrofit>(named("tomtom")).create(TomTomSearchApi::class.java) }

    single { ConnectivityMonitor(androidContext()) }
    single { RetryPolicy.DEFAULT }

    // --- Local persistence (Room) ---
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().trafficReportDao() }
    single { get<AppDatabase>().aggregatedTrafficDao() }
    single { get<AppDatabase>().syncMetaDao() }

    // --- Sample batcher for efficient sample submissions ---
    single {
        com.example.ceylonqueuebuspulse.data.network.SampleBatcher(api = get())
    }

    // --- Repositories ---
    single {
        TrafficAggregationRepository(
            mongoApi = get(),
            aggregatedTrafficDao = get(),
            syncMetaDao = get()
        )
    }

    single {
        TrafficRepository(
            dao = get(),
            appContext = androidContext(),
            mongoApi = get(),
            sampleBatcher = get()
        )
    }

    // --- ViewModels ---
    viewModel { TrafficViewModel(repository = get(), aggregationRepository = get()) }
    viewModel { AuthViewModel(authRepository = get()) }
    viewModel { LocationTrafficViewModel(debugApi = get(), aggregationRepo = get()) }
    viewModel { MapComposeViewModel(tomTomSearchApi = get(), locVm = get()) }
    viewModel { SettingsViewModel(repo = get()) }

    // --- WorkManager workers ---
    worker { AggregationPlannerWorker(appContext = get(), params = get()) }
    worker { MongoAggregationSyncWorker(appContext = get(), params = get()) }
    worker { SevereTrafficAlertWorker(appContext = get(), params = get()) }

    // --- Settings (DataStore) ---
    single { SettingsRepository(context = androidContext()) }
}
