package com.example.waywatch.di

import com.example.waywatch.data.auth.AuthApi
import com.example.waywatch.data.auth.AuthRepository
import com.example.waywatch.data.auth.PendingDeepLinkStore
import com.example.waywatch.data.auth.TokenStore
import com.example.waywatch.data.local.AppDatabase
import com.example.waywatch.data.network.RetrofitProvider
import com.example.waywatch.data.network.model.MongoApi
import com.example.waywatch.data.network.DebugApi
import com.example.waywatch.data.network.TomTomSearchApi
import com.example.waywatch.data.network.OsmRoutesApi
import com.example.waywatch.data.repository.TrafficRepository
import com.example.waywatch.data.repository.TrafficAggregationRepository
import com.example.waywatch.settings.SettingsRepository
import com.example.waywatch.settings.SettingsViewModel
import com.example.waywatch.ui.TrafficViewModel
import com.example.waywatch.ui.auth.AuthViewModel
import com.example.waywatch.traffic.MapComposeViewModel
import com.example.waywatch.traffic.LocationTrafficViewModel
import com.example.waywatch.traffic.RouteCatalogViewModel
import com.example.waywatch.ui.account.AccountViewModel
import com.example.waywatch.util.RetryPolicy
import com.example.waywatch.work.AggregationPlannerWorker
import com.example.waywatch.work.MongoAggregationSyncWorker
import com.example.waywatch.work.SevereTrafficAlertWorker
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

    // Store deep links that need a login first.
    single { PendingDeepLinkStore(androidContext()) }

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

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.tomtom.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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
        com.example.waywatch.data.network.SampleBatcher(api = get())
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
    viewModel { AccountViewModel(api = get()) }
    viewModel { TrafficViewModel(repository = get(), aggregationRepository = get(), locationTracker = get()) }
    viewModel { AuthViewModel(authRepository = get()) }
    viewModel { LocationTrafficViewModel(debugApi = get(), aggregationRepo = get()) }
    viewModel { MapComposeViewModel(tomTomSearchApi = get(), locVm = get()) }
    viewModel { SettingsViewModel(repo = get()) }
    viewModel { RouteCatalogViewModel(osmApi = get()) }

    // --- WorkManager workers ---
    worker { AggregationPlannerWorker(appContext = get(), params = get()) }
    worker { MongoAggregationSyncWorker(appContext = get(), params = get()) }
    worker { SevereTrafficAlertWorker(appContext = get(), params = get()) }

    // --- Settings (DataStore) ---
    single { SettingsRepository(context = androidContext()) }

    // OSM/Overpass routes API (served by our backend)
    single<OsmRoutesApi> { get<retrofit2.Retrofit>(named("mongo")).create(OsmRoutesApi::class.java) }

    // --- Location Tracking ---
    single { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(androidContext()) }
    single<com.example.waywatch.data.location.LocationTracker> { 
        com.example.waywatch.data.location.FusedLocationTracker(fusedLocationClient = get()) 
    }
}
