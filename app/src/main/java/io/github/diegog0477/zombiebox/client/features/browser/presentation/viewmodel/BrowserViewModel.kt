package io.github.diegog0477.zombiebox.client.features.browser.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.browser.domain.model.BrowserDestination
import io.github.diegog0477.zombiebox.client.features.browser.domain.repository.BrowserRepository
import io.github.diegog0477.zombiebox.client.features.browser.domain.repository.BrowserSessionExpired

class BrowserViewModel(
    private val repository: BrowserRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    private data class Command(val action: String, val text: String, val x: Int?, val y: Int?)

    var state = BrowserState()
        private set

    var observer: ((BrowserState) -> Unit)? = null
    @Volatile private var closed = false
    private val lifetime = Any()
    private var activeID = ""
    private val pending = ArrayDeque<Command>()

    fun open(url: String) {
        if (closed) return
        val destination = BrowserDestination.resolve(url)
        if (state.session.isNotEmpty()) {
            input("navigate", destination)
            return
        }
        if (state.loading) return
        state = state.copy(loading = true, failed = false)
        observer?.invoke(state)
        execute {
            var id = ""
            try {
                id = repository.start(destination)
                val abandoned =
                    synchronized(lifetime) {
                        if (closed) true
                        else {
                            activeID = id
                            false
                        }
                    }
                if (abandoned) {
                    repository.stop(id)
                    return@execute
                }
                val frame = repository.frame(id)
                deliver {
                    if (!closed) {
                        state = BrowserState(id, frame)
                        observer?.invoke(state)
                    }
                }
            } catch (_: Exception) {
                if (id.isNotEmpty())
                    try {
                        repository.stop(id)
                    } catch (_: Exception) {}
                synchronized(lifetime) { if (activeID == id) activeID = "" }
                deliver {
                    if (!closed) {
                        state = BrowserState(failed = true)
                        observer?.invoke(state)
                    }
                }
            }
        }
    }

    fun restore(session: String) {
        if (closed || state.session.isNotEmpty() || !session.matches(Regex("[a-f0-9]{32}"))) return
        synchronized(lifetime) { activeID = session }
        state = BrowserState(session = session)
        refresh()
    }

    fun refresh() = update(null, "")

    fun click(x: Int, y: Int) {
        if (x in 0 until 960 && y in 0 until 540) update("click", "", x, y)
    }

    fun move(x: Int, y: Int) {
        if (x in 0 until 960 && y in 0 until 540) update("move", "", x, y)
    }

    fun scroll(vertical: Int) {
        if (vertical in -540..540) update("scroll", "", 0, vertical)
    }

    fun input(action: String, text: String = "") = update(action, text)

    private fun update(action: String?, text: String, x: Int? = null, y: Int? = null) {
        if (closed || state.session.isEmpty()) return
        if (state.loading) {
            if (action == "move" && pending.lastOrNull()?.action == "move") pending.removeLast()
            if (action != null && pending.size < 4) pending.addLast(Command(action, text, x, y))
            return
        }
        val previous = state
        state = state.copy(loading = true, failed = false)
        observer?.invoke(state)
        execute {
            val next =
                try {
                    if (action != null) repository.input(previous.session, action, text, x, y)
                    previous.copy(
                        frame = repository.frame(previous.session),
                        loading = false,
                        failed = false,
                    )
                } catch (_: BrowserSessionExpired) {
                    synchronized(lifetime) { if (activeID == previous.session) activeID = "" }
                    BrowserState(failed = true)
                } catch (_: Exception) {
                    previous.copy(loading = false, failed = true)
                }
            deliver {
                if (!closed) {
                    state = next
                    observer?.invoke(state)
                    if (next.failed || next.session.isEmpty()) pending.clear()
                    else if (pending.isNotEmpty()) {
                        val command = pending.removeFirst()
                        update(command.action, command.text, command.x, command.y)
                    }
                }
            }
        }
    }

    fun close(preserveSession: Boolean = false) {
        if (closed) return
        val id =
            synchronized(lifetime) {
                closed = true
                val current = activeID
                activeID = ""
                current
            }
        state = BrowserState()
        pending.clear()
        observer = null
        if (id.isNotEmpty() && !preserveSession)
            execute {
                try {
                    repository.stop(id)
                } catch (_: Exception) {}
            }
    }
}
