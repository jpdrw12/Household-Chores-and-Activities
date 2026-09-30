package com.jpdrw.household

import android.app.Application
import com.jpdrw.household.data.AppDatabase
import com.jpdrw.household.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class HouseholdApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.get(this, applicationScope) }
    val repository by lazy { Repository(database) }
}
