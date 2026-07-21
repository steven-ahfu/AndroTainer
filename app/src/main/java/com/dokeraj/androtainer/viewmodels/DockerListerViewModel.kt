package com.dokeraj.androtainer.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dokeraj.androtainer.models.ContainerActionType
import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerInspectInfo
import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.repositories.DockerListerRepo
import com.dokeraj.androtainer.util.DataState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DockerListerViewModel @Inject constructor(
    private val dockerListerRepo: DockerListerRepo,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _dataState: MutableLiveData<DataState<List<Kontainer>>> = MutableLiveData()
    private var currentList: List<Kontainer> = listOf()

    val dataState: LiveData<DataState<List<Kontainer>>>
        get() = _dataState

    /** live stats per container id — a SEPARATE stream so the details fragment's
     * pop-on-Success observer of [dataState] is never falsely triggered */
    private val _statsState: MutableLiveData<Map<String, KontainerStats>> = MutableLiveData()
    val statsState: LiveData<Map<String, KontainerStats>>
        get() = _statsState

    /** details-page extras (stats + inspect) — also a separate stream */
    private val _detailExtras:
            MutableLiveData<DataState<Pair<KontainerStats?, KontainerInspectInfo?>>> =
        MutableLiveData()
    val detailExtras: LiveData<DataState<Pair<KontainerStats?, KontainerInspectInfo?>>>
        get() = _detailExtras

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setStateEvent(mainStateEvent: MainStateEvent) {
        viewModelScope.launch {
            when (mainStateEvent) {
                is MainStateEvent.GetKontejneri ->
                    dockerListerRepo.getDocContainers(mainStateEvent.jwt,
                        mainStateEvent.url,
                        mainStateEvent.isUsingApiKey)
                        .onEach { dlDataState ->
                            when (dlDataState) {
                                is DataState.Success -> {
                                    currentList = dlDataState.data
                                    _dataState.value = dlDataState
                                }
                                is DataState.Error -> {
                                    _dataState.value = dlDataState
                                }
                                is DataState.Loading -> {
                                    _dataState.value = dlDataState
                                }

                                else -> {}
                            }
                        }.launchIn(viewModelScope)


                is MainStateEvent.StartStopKontejneri -> {
                    /** card actions are id-based: the RecyclerView position indexes the FILTERED
                     * list, so it must never be used to mutate the full currentList */
                    val itemIndex =
                        currentList.indexOfFirst { it.id == mainStateEvent.containerId }
                    dockerListerRepo.startStopDokerContainer(mainStateEvent.jwt,
                        mainStateEvent.url,
                        mainStateEvent.isUsingApiKey,
                        itemIndex)
                        .onEach { ssDataState ->
                            when (ssDataState) {

                                is DataState.CardLoading -> {
                                    /** change the state and status to transitioning */
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { itemToChange ->
                                            if (itemToChange.id == mainStateEvent.containerId)
                                                itemToChange.copy(status = if (mainStateEvent.containerActionType == ContainerActionType.START) "Starting" else "Exiting",
                                                    state = ContainerStateType.TRANSITIONING)
                                            else
                                                itemToChange
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.CardLoading(modifiedKontainers,
                                        ssDataState.itemIndex)
                                }

                                is DataState.CardSuccess -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { itemToChange ->
                                            if (itemToChange.id == mainStateEvent.containerId)
                                                itemToChange.copy(status = if (mainStateEvent.containerActionType == ContainerActionType.START) "Started just now" else "Exited just now",
                                                    state = if (mainStateEvent.containerActionType == ContainerActionType.START) ContainerStateType.RUNNING else ContainerStateType.EXITED)
                                            else
                                                itemToChange
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.CardSuccess(
                                        modifiedKontainers, ssDataState.itemIndex)
                                }
                                is DataState.CardError -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { curItem ->
                                            if (curItem.id == mainStateEvent.containerId)
                                                curItem.copy(status = "Refresh by swiping down",
                                                    state = ContainerStateType.ERRORED)
                                            else
                                                curItem
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.CardError(modifiedKontainers,
                                        itemIndex)
                                }
                                else -> {}
                            }

                        }.launchIn(viewModelScope)
                }

                is MainStateEvent.RestartKontejneri -> {
                    val itemIndex =
                        currentList.indexOfFirst { it.id == mainStateEvent.containerId }
                    dockerListerRepo.restartContainer(mainStateEvent.jwt,
                        mainStateEvent.url,
                        mainStateEvent.isUsingApiKey,
                        itemIndex)
                        .onEach { ssDataState ->
                            when (ssDataState) {

                                is DataState.CardLoading -> {
                                    /** change the state and status to transitioning */
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { itemToChange ->
                                            if (itemToChange.id == mainStateEvent.containerId)
                                                itemToChange.copy(status = "Restarting",
                                                    state = ContainerStateType.RESTARTING)
                                            else
                                                itemToChange
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.CardLoading(modifiedKontainers,
                                        ssDataState.itemIndex)
                                }

                                is DataState.CardSuccess -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { itemToChange ->
                                            if (itemToChange.id == mainStateEvent.containerId)
                                                itemToChange.copy(status = "Restarted just now",
                                                    state = ContainerStateType.RUNNING)
                                            else
                                                itemToChange
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.CardSuccess(
                                        modifiedKontainers, ssDataState.itemIndex)
                                }
                                is DataState.CardError -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { curItem ->
                                            if (curItem.id == mainStateEvent.containerId)
                                                curItem.copy(status = "Refresh by swiping down",
                                                    state = ContainerStateType.ERRORED)
                                            else
                                                curItem
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.CardError(modifiedKontainers,
                                        itemIndex)
                                }
                                else -> {}
                            }

                        }.launchIn(viewModelScope)
                }

                is MainStateEvent.InitializeView -> {
                    currentList = mainStateEvent.lista
                    _dataState.value = DataState.Success(mainStateEvent.lista)
                }

                is MainStateEvent.DeleteContaier -> {
                    dockerListerRepo.deleteContainer(mainStateEvent.jwt,
                        mainStateEvent.url,
                        mainStateEvent.isUsingApiKey,
                        mainStateEvent.selectedItem)
                        .onEach { ssState ->
                            when (ssState) {

                                is DataState.DeleteLoading -> {
                                    /** result back to View */
                                    _dataState.value =
                                        DataState.DeleteLoading(currentList, ssState.item)
                                }

                                is DataState.DeleteSuccess -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.filterNot { i ->
                                            i.id == ssState.item.id
                                        }

                                    currentList = modifiedKontainers
                                    /** result back to View */
                                    _dataState.value = DataState.Success(modifiedKontainers)
                                }
                                is DataState.Error -> {
                                    /** result back to View */
                                    _dataState.value = DataState.Error(ssState.exception)
                                }

                                else -> {}
                            }
                        }.launchIn(viewModelScope)
                }

                is MainStateEvent.SetNone -> {
                    _dataState.value = DataState.None
                }
                is MainStateEvent.SetSuccess -> {
                    _dataState.value = DataState.Success(currentList)
                }

                is MainStateEvent.GetStats -> {
                    /** reset to placeholders, then post the accumulating map as each
                     * container's sample arrives */
                    _statsState.value = emptyMap()
                    dockerListerRepo.getStatsBatch(mainStateEvent.jwt,
                        mainStateEvent.urlTemplate,
                        mainStateEvent.isUsingApiKey,
                        currentList)
                        .onEach { (containerId, stats) ->
                            _statsState.value =
                                (_statsState.value ?: emptyMap()) + (containerId to stats)
                        }.launchIn(viewModelScope)
                }

                is MainStateEvent.GetContainerExtras -> {
                    dockerListerRepo.getContainerDetailExtras(mainStateEvent.jwt,
                        mainStateEvent.statsUrl,
                        mainStateEvent.inspectUrl,
                        mainStateEvent.isUsingApiKey)
                        .onEach { extras ->
                            _detailExtras.value = extras
                        }.launchIn(viewModelScope)
                }

                is MainStateEvent.PauseKontejneri -> {
                    val itemIndex =
                        currentList.indexOfFirst { it.id == mainStateEvent.containerId }
                    dockerListerRepo.pauseUnpauseContainer(mainStateEvent.jwt,
                        mainStateEvent.url,
                        mainStateEvent.isUsingApiKey,
                        itemIndex)
                        .onEach { pDataState ->
                            when (pDataState) {
                                is DataState.CardLoading -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { itemToChange ->
                                            if (itemToChange.id == mainStateEvent.containerId)
                                                itemToChange.copy(
                                                    status = if (mainStateEvent.isPause) "Pausing" else "Unpausing",
                                                    state = ContainerStateType.TRANSITIONING)
                                            else
                                                itemToChange
                                        }
                                    currentList = modifiedKontainers
                                    _dataState.value = DataState.CardLoading(modifiedKontainers,
                                        pDataState.itemIndex)
                                }
                                is DataState.CardSuccess -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { itemToChange ->
                                            if (itemToChange.id == mainStateEvent.containerId)
                                                itemToChange.copy(
                                                    status = if (mainStateEvent.isPause) "Paused just now" else "Started just now",
                                                    state = if (mainStateEvent.isPause) ContainerStateType.PAUSED else ContainerStateType.RUNNING)
                                            else
                                                itemToChange
                                        }
                                    currentList = modifiedKontainers
                                    _dataState.value = DataState.CardSuccess(
                                        modifiedKontainers, pDataState.itemIndex)
                                }
                                is DataState.CardError -> {
                                    val modifiedKontainers: List<Kontainer> =
                                        currentList.map { curItem ->
                                            if (curItem.id == mainStateEvent.containerId)
                                                curItem.copy(status = "Refresh by swiping down",
                                                    state = ContainerStateType.ERRORED)
                                            else
                                                curItem
                                        }
                                    currentList = modifiedKontainers
                                    _dataState.value = DataState.CardError(modifiedKontainers,
                                        itemIndex)
                                }
                                else -> {}
                            }
                        }.launchIn(viewModelScope)
                }
            }
        }
    }
}

sealed class MainStateEvent {
    data class GetKontejneri(val jwt: String?, val url: String, val isUsingApiKey: Boolean) :
        MainStateEvent()

    data class StartStopKontejneri(
        val jwt: String?,
        val url: String,
        val isUsingApiKey: Boolean,
        val containerId: String,
        val containerActionType: ContainerActionType,
    ) : MainStateEvent()

    data class RestartKontejneri(
        val jwt: String?,
        val url: String,
        val isUsingApiKey: Boolean,
        val containerId: String,
    ) : MainStateEvent()

    data class InitializeView(val lista: List<Kontainer>) : MainStateEvent()

    data class DeleteContaier(
        val jwt: String?,
        val url: String,
        val isUsingApiKey: Boolean,
        val selectedItem: Kontainer,
    ) :
        MainStateEvent()

    object SetNone : MainStateEvent()
    object SetSuccess : MainStateEvent()

    /** batch stats for all running containers; urlTemplate keeps {containerId} */
    data class GetStats(
        val jwt: String?,
        val urlTemplate: String,
        val isUsingApiKey: Boolean,
    ) : MainStateEvent()

    /** stats + inspect for the details page; statsUrl null when not running */
    data class GetContainerExtras(
        val jwt: String?,
        val statsUrl: String?,
        val inspectUrl: String,
        val isUsingApiKey: Boolean,
    ) : MainStateEvent()

    data class PauseKontejneri(
        val jwt: String?,
        val url: String,
        val isUsingApiKey: Boolean,
        val containerId: String,
        val isPause: Boolean,
    ) : MainStateEvent()
}