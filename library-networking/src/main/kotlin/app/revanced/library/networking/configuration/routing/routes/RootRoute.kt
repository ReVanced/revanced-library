package app.revanced.library.networking.configuration.routing.routes

import app.revanced.library.logging.Logger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import java.util.*

internal fun Route.configureRootRoute() {
    route("/") {
        configureAboutRoute()
        configureLoggingRoute()
    }
}

/**
 * Route to get information about the server.
 */
private fun Route.configureAboutRoute() {
    val name = this::class.java.getResourceAsStream(
        "/app/revanced/library/networking/version.properties",
    )?.use { stream ->
        Properties().apply {
            load(stream)
        }.let {
            "ReVanced Networking Library v${it.getProperty("version")}"
        }
    } ?: "ReVanced Networking Library"

    handle {
        call.respondText(name)
    }
}

// TODO: Fix clients disconnecting from the server.
/**
 * Route to get logs from the server.
 */
private fun Route.configureLoggingRoute() {
    val sessions = Collections.synchronizedSet<DefaultWebSocketSession?>(LinkedHashSet())

    Logger.addHandler({ log: String, level: java.util.logging.Level, loggerName: String? ->
        runBlocking {
            sessions.forEach {
                try {
                    it.send("[$loggerName] $level: $log")
                } catch (e: Exception) {
                    sessions -= it
                }
            }
        }
    }, {}, {})

    webSocket("/logs") {
        sessions += this
    }
}
