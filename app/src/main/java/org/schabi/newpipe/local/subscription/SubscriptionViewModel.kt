package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import java.util.concurrent.TimeUnit

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)
    private var subscriptionManager = SubscriptionManager(application)

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    private val mutableFeedGroupsLiveData = MutableLiveData<List<Group>>()
    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData
    val feedGroupsLiveData: LiveData<List<Group>> = mutableFeedGroupsLiveData

    private val searchQuery = MutableLiveData<String>()

    /** Sort modes for the subscription list. "Added" order uses the subscription row id
     *  (auto-increment), i.e. the order in which you subscribed. */
    enum class SortMode(val label: String) {
        NEWEST_ADDED("Newest subscribed first"),
        OLDEST_ADDED("Oldest subscribed first"),
        NAME_AZ("Name A-Z"),
        NAME_ZA("Name Z-A"),
        MOST_SUBSCRIBERS("Most subscribers"),
        FEWEST_SUBSCRIBERS("Fewest subscribers")
    }

    private val sortModePrefKey = "subscription_sort_mode"
    private val sortMode = MutableLiveData<SortMode>(
        runCatching {
            SortMode.valueOf(
                PreferenceManager.getDefaultSharedPreferences(application)
                    .getString(sortModePrefKey, SortMode.NAME_AZ.name) ?: SortMode.NAME_AZ.name
            )
        }.getOrDefault(SortMode.NAME_AZ)
    )

    fun currentSortMode(): SortMode = sortMode.value ?: SortMode.NAME_AZ

    fun updateSortMode(mode: SortMode) {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putString(sortModePrefKey, mode.name).apply()
        sortMode.value = mode
    }
    val filteredSubscriptionsLiveData = MediatorLiveData<List<Group>>()

    init {
        // Setup the filtering logic
        filteredSubscriptionsLiveData.addSource(stateLiveData) { state ->
            if (state is SubscriptionState.LoadedState) {
                applyFilter(state.subscriptions, searchQuery.value)
            }
        }

        filteredSubscriptionsLiveData.addSource(sortMode) {
            val currentState = stateLiveData.value
            if (currentState is SubscriptionState.LoadedState) {
                applyFilter(currentState.subscriptions, searchQuery.value)
            }
        }

        filteredSubscriptionsLiveData.addSource(searchQuery) { query ->
            val currentState = stateLiveData.value
            if (currentState is SubscriptionState.LoadedState) {
                applyFilter(currentState.subscriptions, query)
            }
        }
    }

    fun updateSearchQuery(query: String?) {
        searchQuery.value = query
    }

    fun getCurrentSearchQuery(): String {
        return searchQuery.value ?: ""
    }

    private fun applyFilter(subscriptions: List<Group>, query: String?) {
        val lowercaseQuery = query?.lowercase().orEmpty()
        val others = ArrayList<Group>()
        val channels = ArrayList<ChannelItem>()
        for (group in subscriptions) {
            if (group is ChannelItem) {
                if (lowercaseQuery.isBlank() ||
                    group.infoItem.name.lowercase().contains(lowercaseQuery)
                ) {
                    channels.add(group)
                }
            } else {
                others.add(group) // keep other items
            }
        }

        val sorted: List<ChannelItem> = when (currentSortMode()) {
            SortMode.NEWEST_ADDED -> channels.sortedByDescending { it.subscriptionId }
            SortMode.OLDEST_ADDED -> channels.sortedBy { it.subscriptionId }
            SortMode.NAME_AZ -> channels.sortedBy { it.infoItem.name.lowercase() }
            SortMode.NAME_ZA -> channels.sortedByDescending { it.infoItem.name.lowercase() }
            SortMode.MOST_SUBSCRIBERS -> channels.sortedByDescending { it.infoItem.subscriberCount }
            SortMode.FEWEST_SUBSCRIBERS -> channels.sortedBy {
                if (it.infoItem.subscriberCount < 0) Long.MAX_VALUE else it.infoItem.subscriberCount
            }
        }

        filteredSubscriptionsLiveData.value = others + sorted
    }

    private var feedGroupItemsDisposable = Flowable.combineLatest(
        feedDatabaseManager.groups(),
        feedDatabaseManager.groupSubscriptionCounts().startWithItem(emptyList())
    ) { groups, counts ->
        val countById = counts.associate { it.groupId to it.memberCount }
        groups.map { FeedGroupCardItem(it, countById[it.uid] ?: 0) }
    }
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableFeedGroupsLiveData.postValue(it) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    private var stateItemsDisposable = subscriptionManager.subscriptions()
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .map { it.map { entity -> ChannelItem(entity.toChannelInfoItem(), entity.uid, ChannelItem.ItemVersion.MINI) } }
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableStateLiveData.postValue(SubscriptionState.LoadedState(it)) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    override fun onCleared() {
        super.onCleared()
        stateItemsDisposable.dispose()
        feedGroupItemsDisposable.dispose()
    }

    sealed class SubscriptionState {
        data class LoadedState(val subscriptions: List<Group>) : SubscriptionState()
        data class ErrorState(val error: Throwable? = null) : SubscriptionState()
    }
}
