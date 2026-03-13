package com.example.waywatch.data.repository

import com.example.waywatch.data.UserLocationUpdate

interface ILocationSubmitter {
    suspend fun submitUserUpdate(update: UserLocationUpdate): AppResult<Unit>
}
