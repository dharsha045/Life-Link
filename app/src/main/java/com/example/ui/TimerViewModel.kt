package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TimerEntity
import com.example.data.TimerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder(val displayName: String) {
    TIME_LEFT_ASC("Time Remaining (Fewest)"),
    TIME_LEFT_DESC("Time Remaining (Most)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    CATEGORY("Category Group")
}

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimerRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TimerRepository(application, database.timerDao())
    }

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TIME_LEFT_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Background ticker flow that updates every second to force UI state recalculation
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    // Combined Flow containing all filtered and sorted results in real time
    val filteredTimers: StateFlow<List<TimerEntity>> = combine(
        repository.allTimers,
        _searchText,
        _selectedCategory,
        _sortOrder,
        tickerFlow
    ) { timers, query, category, sort, _ ->
        timers.filter { timer ->
            val matchesQuery = timer.name.contains(query, ignoreCase = true)
            val matchesCategory = category == "All" || timer.category == category
            matchesQuery && matchesCategory
        }.sortedWith { a, b ->
            when (sort) {
                SortOrder.TIME_LEFT_ASC -> {
                    // Completed items sorted first or last depending on preference, let's keep them sorted first
                    a.getRemainingSeconds().compareTo(b.getRemainingSeconds())
                }
                SortOrder.TIME_LEFT_DESC -> {
                    b.getRemainingSeconds().compareTo(a.getRemainingSeconds())
                }
                SortOrder.NAME_ASC -> {
                    a.name.lowercase().compareTo(b.name.lowercase())
                }
                SortOrder.NAME_DESC -> {
                    b.name.lowercase().compareTo(a.name.lowercase())
                }
                SortOrder.CATEGORY -> {
                    a.category.compareTo(b.category)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchText(query: String) {
        _searchText.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSortOrder(sort: SortOrder) {
        _sortOrder.value = sort
    }

    fun addTimer(name: String, category: String, days: Int, hours: Int, minutes: Int, seconds: Int) {
        viewModelScope.launch {
            val totalSeconds = (days * 24 * 3600L) + (hours * 3600L) + (minutes * 60L) + seconds
            if (totalSeconds > 0) {
                repository.insertTimer(
                    name = name.ifBlank { "Clash Upgrade" },
                    category = category,
                    durationSeconds = totalSeconds
                )
            }
        }
    }

    fun togglePause(timer: TimerEntity) {
        viewModelScope.launch {
            repository.togglePauseTimer(timer)
        }
    }

    fun resetTimer(timer: TimerEntity) {
        viewModelScope.launch {
            repository.resetTimer(timer)
        }
    }

    fun deleteTimer(timer: TimerEntity) {
        viewModelScope.launch {
            repository.deleteTimer(timer)
        }
    }

    fun updateTimer(
        timer: TimerEntity,
        name: String,
        category: String,
        newDuration: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = timer.copy(
                name = name.ifBlank { "Clash Upgrade" },
                category = category,
                durationSeconds = newDuration,
                startTimeMs = now,
                targetTimeMs = now + (newDuration * 1000),
                isPaused = false,
                pausedRemainingSeconds = newDuration,
                isCompletedNotificationShown = false
            )
            repository.updateTimer(updated)
        }
    }
}
