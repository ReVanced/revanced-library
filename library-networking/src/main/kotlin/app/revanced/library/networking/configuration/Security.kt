package app.revanced.library.networking.configuration

import app.revanced.library.networking.Server
import io.ktor.server.application.*
import io.ktor.server.auth.*

/**
 * Configures the security for the application.
 *
 * @param securityConfiguration The security configuration.
 */
internal fun Application.configureSecurity(
    securityConfiguration: Server.SecurityConfiguration,
) {
    install(Authentication) {
        basic {
            validate { credentials ->
                if (credentials.name == securityConfiguration.username &&
                    credentials.password == securityConfiguration.password
                ) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}
