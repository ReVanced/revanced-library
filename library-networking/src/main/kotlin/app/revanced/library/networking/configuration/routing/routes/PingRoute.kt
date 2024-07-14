package app.revanced.library.networking.configuration.routing.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Route to check if the server is up.
 */
internal fun Route.configurePingRoute() {
    head("/ping") {
        call.respond(HttpStatusCode.OK)
    }
}
