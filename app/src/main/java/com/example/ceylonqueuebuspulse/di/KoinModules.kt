package com.example.ceylonqueuebuspulse.di

import com.example.ceylonqueuebuspulse.data.auth.AuthApi
import com.example.ceylonqueuebuspulse.data.auth.AuthRepository
import com.example.ceylonqueuebuspulse.data.auth.TokenStore
import com.example.ceylonqueuebuspulse.data.local.AppDatabase
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.network.model.MongoApi
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel
import com.example.ceylonqueuebuspulse.util.ConnectivityMonitor
import com.example.ceylonqueuebuspulse.util.RetryPolicy
import com.example.ceylonqueuebuspulse.work.AggregationPlannerWorker
import com.example.ceylonqueuebuspulse.work.MongoAggregationSyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Central Koin module definitions with error handling and retry logic support.
 */
val appModule = module {
    // --- Networking & Connectivity ---
    single { TokenStore(androidContext()) }

    // Auth API must work even when we don't yet have a token.
    single(named("auth")) { RetrofitProvider.mongoRetrofit() }
    single<AuthApi> { get<retrofit2.Retrofit>(named("auth")).create(AuthApi::class.java) }
    single { AuthRepository(api = get(), tokenStore = get()) }

    // Main API uses tokens + auto-refresh
    single(named("mongo")) { RetrofitProvider.mongoRetrofit(tokenStore = get(), authRepository = get()) }
    single<MongoApi> { get<retrofit2.Retrofit>(named("mongo")).create(MongoApi::class.java) }

    single { ConnectivityMonitor(androidContext()) }
    single { RetryPolicy.DEFAULT }

    // --- Local persistence (Room) ---
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().trafficReportDao() }
    single { get<AppDatabase>().aggregatedTrafficDao() }
    single { get<AppDatabase>().syncMetaDao() }

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
            aggregationRepository = get(),
            mongoApi = get()
        )
    }

    // --- ViewModels ---
    viewModel { TrafficViewModel(repository = get(), aggregationRepository = get()) }
    viewModel { AuthViewModel(authRepository = get()) }

    // --- WorkManager workers ---
    worker { AggregationPlannerWorker(appContext = get(), params = get()) }
    worker { MongoAggregationSyncWorker(appContext = get(), params = get()) }
}
