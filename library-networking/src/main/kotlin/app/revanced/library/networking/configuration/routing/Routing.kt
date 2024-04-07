package app.revanced.library.networking.configuration.routing

import app.revanced.library.networking.configuration.routing.routes.configurePatchBundlesRoute
import app.revanced.library.networking.configuration.routing.routes.configurePatcherRoute
import app.revanced.library.networking.configuration.routing.routes.configurePingRoute
import app.revanced.library.networking.configuration.routing.routes.configureRootRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

/**
 * Configures the routing for the application.
 */
internal fun Application.configureRouting() {
    routing {
        authenticate {
            configureRootRoute()
            configurePingRoute()
            configurePatchBundlesRoute()
            configurePatcherRoute()
        }
    }
}
