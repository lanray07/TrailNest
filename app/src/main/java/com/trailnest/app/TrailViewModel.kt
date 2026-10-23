package com.trailnest.app

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trailnest.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val android.content.Context.preferences by preferencesDataStore("trailnest_preferences")
class TrailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TrailDatabase.create(app).dao()
    val walks = dao.walks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routes = dao.routes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val prefs = app.preferences.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyPreferences())
    fun toggle(key: String) { viewModelScope.launch { getApplication<Application>().preferences.edit { it[booleanPreferencesKey(key)] = !(it[booleanPreferencesKey(key)] ?: false) } } }
    fun setting(key: String, value: String) { viewModelScope.launch { getApplication<Application>().preferences.edit { it[stringPreferencesKey(key)] = value } } }
    fun save(walk: Walk) { viewModelScope.launch { dao.save(walk) } }
    fun save(route: ImportedRoute) { viewModelScope.launch { dao.save(route) } }
    fun deleteWalk(id: String) { viewModelScope.launch { dao.deleteWalk(id) } }
    fun deleteRoute(id: String) { viewModelScope.launch { dao.deleteRoute(id) } }
}
