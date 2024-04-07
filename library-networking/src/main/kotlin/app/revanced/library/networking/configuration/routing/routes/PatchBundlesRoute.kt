package app.revanced.library.networking.configuration.routing.routes

import app.revanced.library.networking.parameters
import app.revanced.library.networking.services.PatchBundleService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import io.ktor.server.util.*
import org.koin.ktor.ext.get

/**
 * Route to handle all patch bundle related requests such as creating, reading, updating and deleting patch bundles.
 */
internal fun Route.configurePatchBundlesRoute() {
    val patchBundleService = get<PatchBundleService>()

    route("/patch-bundles") {
        get {
            call.respond(patchBundleService.patchBundleNames)
        }

        post("/add") {
            val patchBundleName: String by parameters
            val patchBundleFilePath = parameters["patchBundleFilePath"]

            if (patchBundleFilePath != null) {
                val patchBundleIntegrationsFilePath = parameters["patchBundleIntegrationsFilePath"]

                patchBundleService.addPersistentlyLocalPatchBundle(
                    patchBundleName,
                    patchBundleFilePath,
                    patchBundleIntegrationsFilePath,
                )
            } else {
                val patchBundleDownloadLink: String by parameters
                val patchBundleIntegrationsDownloadLink = parameters["patchBundleIntegrationsDownloadLink"]

                patchBundleService.addPersistentlyDownloadPatchBundle(
                    patchBundleName,
                    patchBundleDownloadLink,
                    patchBundleIntegrationsDownloadLink,
                )
            }
        }

        post("/remove") {
            val patchBundleName: String by parameters

            patchBundleService.removePersistentlyPatchBundle(patchBundleName)
        }

        post("/refresh") {
            patchBundleService.refresh()
        }
    }
}
