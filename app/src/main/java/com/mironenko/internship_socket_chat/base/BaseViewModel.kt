package com.mironenko.internship_socket_chat.base

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mironenko.internship_socket_chat.ui.UserAuthorizationAction
import com.mironenko.internship_socket_chat.util.delegate
import com.mironenko.internship_socket_chat.util.network.CheckNetworkStatus
import com.mironenko.internship_socket_chat.util.network.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseViewModel<State, Action>(
    private val interactors: Set<Interactor<State, Action>>,
    private val reducer: Reducer<State, Action>,
    private val networkStatus: CheckNetworkStatus
) : ViewModel() {

    private val mutableState = MutableLiveData(reducer.initialState)
    private var stateValue by mutableState.delegate()
    val state: LiveData<State> = mutableState

    init {
        viewModelScope.launch {
            flowOf(
                *interactors.filterIsInstance<SideEffectInteractor<State, Action>>().map {
                    it.sideEffectFlow
                }.toTypedArray()
            ).flattenConcat()
                .collectLatest {
                    action(it)
                }
        }
    }

    @MainThread
    protected fun action(action: Action) {
        stateValue = reducer.reduce(stateValue, action)
        interactors.filter { it.canHandle(action) }.forEach { interactor ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = interactor.invoke(stateValue, action)
                withContext(Dispatchers.Main) {
                    action(result)
                }
            }
        }
    }
}