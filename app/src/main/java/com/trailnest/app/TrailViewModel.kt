package com.trailnest.app

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trailnest.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.trailnest.app.domain.*
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val android.content.Context.preferences by preferencesDataStore("trailnest_preferences")
class TrailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TrailDatabase.create(app).dao()
    val walks = dao.walks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routes = dao.routes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val prefs = app.preferences.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyPreferences())
    val photos = dao.photos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    val photoBusy = MutableStateFlow(false)
    private val photoStore = PhotoStore(app)
    companion object { private val photoMutex = Mutex() }
    init { viewModelScope.launch(Dispatchers.IO) { photoMutex.withLock { photoStore.prune(dao.photoFileNames().toSet()) } } }
    private val checklistKey = stringPreferencesKey("personal_checklist_v1")
    fun checklist(prefs: Preferences): List<ChecklistItem> {
        val legacy = PersonalChecklist.initial { prefs[booleanPreferencesKey("kit_$it")] == true }
        val raw = prefs[checklistKey] ?: return legacy
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i -> array.getJSONObject(i).let { ChecklistItem(it.getString("id"), it.getString("title"), it.getBoolean("checked")) } }
        }.getOrElse { legacy }
    }
    private fun updateChecklist(change: (List<ChecklistItem>) -> List<ChecklistItem>) {
        viewModelScope.launch { getApplication<Application>().preferences.edit { prefs ->
            val items = change(checklist(prefs))
            prefs[checklistKey] = JSONArray().apply { items.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("checked", it.checked)) } }.toString()
        } }
    }
    fun checkItem(id: String) = updateChecklist { items -> items.map { if (it.id == id) it.copy(checked = !it.checked) else it } }
    fun addItem(title: String) { val clean = PersonalChecklist.validateTitle(title); updateChecklist { if (it.size < 100) it + ChecklistItem(UUID.randomUUID().toString(), clean) else it } }
    fun renameItem(id: String, title: String) { val clean = PersonalChecklist.validateTitle(title); updateChecklist { items -> items.map { if (it.id == id) it.copy(title = clean) else it } } }
    fun removeItem(id: String) = updateChecklist { items -> items.filterNot { it.id == id } }
    fun moveItem(id: String, offset: Int) = updateChecklist { items -> items.toMutableList().apply { val from = indexOfFirst { it.id == id }; val to = from + offset; if (from >= 0 && to in indices) add(to, removeAt(from)) } }
    fun resetChecklist() = updateChecklist(PersonalChecklist::reset)
    fun restoreChecklist() = updateChecklist { PersonalChecklist.initial() }
    fun toggle(key: String) { viewModelScope.launch { getApplication<Application>().preferences.edit { it[booleanPreferencesKey(key)] = !(it[booleanPreferencesKey(key)] ?: false) } } }
    fun setting(key: String, value: String) { viewModelScope.launch { getApplication<Application>().preferences.edit { it[stringPreferencesKey(key)] = value } } }
    fun save(walk: Walk) { viewModelScope.launch { dao.save(walk) } }
    fun save(route: ImportedRoute) { viewModelScope.launch { dao.save(route) } }
    fun deleteWalk(id: String) { viewModelScope.launch { photoMutex.withLock {
        val files = dao.photosFor(id)
        dao.deleteWalk(id)
        withContext(Dispatchers.IO) { files.forEach { photoStore.remove(it.fileName) } }
    } } }
    fun addPhoto(walkId: String, uri: Uri) {
        if (photoBusy.value) return
        photoBusy.value = true
        viewModelScope.launch {
            try { photoMutex.withLock {
                require(dao.walk(walkId) != null) { "Save the walk before adding a photo." }
                require(dao.photosFor(walkId).size < 8) { "Each walk can contain up to eight photos." }
                val name = withContext(Dispatchers.IO) { photoStore.import(uri) }
                try { dao.addPhoto(WalkPhoto(UUID.randomUUID().toString(), walkId, name)) }
                catch (error: Exception) { withContext(Dispatchers.IO) { photoStore.remove(name) }; throw error }
            } } catch (error: Exception) { message.value = error.message ?: "Photo import failed." }
            finally { photoBusy.value = false }
        }
    }
    fun deletePhoto(photo: WalkPhoto) { viewModelScope.launch { photoMutex.withLock { dao.deletePhoto(photo.id); withContext(Dispatchers.IO) { photoStore.remove(photo.fileName) } } } }
    fun deleteRoute(id: String) { viewModelScope.launch { dao.deleteRoute(id) } }
}
