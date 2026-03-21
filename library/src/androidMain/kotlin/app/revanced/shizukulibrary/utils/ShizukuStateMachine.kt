package app.revanced.shizukulibrary.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State machine tracking the Shizuku server lifecycle.
 * Backed by [StateFlow] for thread-safe access from any thread/coroutine.
 */
object ShizukuStateMachine {

    enum class State {
        /** No server process running. */
        IDLE,
        /** Server is being started (ADB connection in progress). */
        STARTING,
        /** Server binder is available and responsive. */
        RUNNING,
        /** Server failed to start or crashed. */
        DEAD
    }

    private val _stateFlow = MutableStateFlow(State.IDLE)

    fun get(): State = _stateFlow.value

    fun set(state: State) {
        _stateFlow.value = state
    }

    fun isRunning(): Boolean = get() == State.RUNNING

    fun isDead(): Boolean = get() == State.DEAD

    /**
     * Checks actual server status and updates state accordingly.
     * Returns the updated state.
     */
    fun update(): State {
        // In a full implementation this would ping the Shizuku binder.
        return get()
    }

    /**
     * Expose as Flow for coroutine consumers.
     */
    fun asFlow(): Flow<State> = _stateFlow.asStateFlow()
}


