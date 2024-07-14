package app.revanced.library.networking.configuration.routing.routes

import app.revanced.library.networking.configuration.repository.InstallerRepository
import app.revanced.library.networking.models.Patch
import app.revanced.library.networking.parameters
import app.revanced.library.networking.services.PatcherService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import io.ktor.server.util.*
import org.koin.ktor.ext.get
import java.io.File

/**
 * Route to the patcher to handles all patcher related requests such as patching, signing and installing patched apps.
 */
internal fun Route.configurePatcherRoute() {
    route("/patcher") {
        configureAppsRoute()
        configurePatchesRoute()
        configurePatchOptionsRoute()
        configurePatchRoute()
        configureSignRoute()
        configureInstallationRoute()
        configureCleanRoute()
    }
}

/**
 * Route to list all patchable apps that can be patched.
 */
private fun Route.configureAppsRoute() {
    val patcherService = get<PatcherService>()

    get("/apps") {
        val universal = parameters.contains("universal")

        call.respond(patcherService.getInstalledApps(universal))
    }
}

/**
 * Route to get all patches for a specific app and version.
 */
private fun Route.configurePatchesRoute() {
    val patcherService = get<PatcherService>()

    get("/patches") {
        val app = parameters["app"]
        val version = parameters["version"]
        val universal = "universal" in parameters

        call.respond(patcherService.getPatches(app, version, universal))
    }
}

/**
 * Route to get and set patch options.
 */
private fun Route.configurePatchOptionsRoute() {
    val patcherService = get<PatcherService>()

    route("/options") {
        get {
            val app: String by parameters
            val patch: String by parameters

            call.respond(patcherService.getPatchOptions(patchName = patch, app))
        }

        post {
            // Abuse serialization capabilities of Patch.PatchOption
            // because Patch.KeyValuePatchOption isn't serializable.
            // ONLY the Patch.PatchOption.key and Patch.PatchOption.value properties are used here.
            val patchOptions: Set<Patch.PatchOption<*>> by call.receive()
            val patch: String by parameters
            val app: String by parameters

            patcherService.setPatchOptions(
                // Use Patch.PatchOption.default for Patch.KeyValuePatchOption.value.
                patchOptions = patchOptions.map { Patch.KeyValuePatchOption(it) }.toSet(),
                patchName = patch,
                app,
            )

            call.respond(HttpStatusCode.OK)
        }

        delete {
            val patch: String by parameters
            val app: String by parameters

            patcherService.resetPatchOptions(patchName = patch, app)

            call.respond(HttpStatusCode.OK)
        }
    }
}

/**
 * Route to patch an app with a set of patches.
 */
private fun Route.configurePatchRoute() {
    val installerRepository = get<InstallerRepository>()
    val patcherService = get<PatcherService>()

    post("/patch") {
        val patchNames = parameters.getAll("patch")?.toSet() ?: emptySet()
        val multithreading = "multithreading" in parameters

        // TODO: The path to the APK must be local to the server, otherwise it will not work.
        val apkPath = parameters["app"]?.let {
            installerRepository.installer.getInstallation(it)?.apkFilePath
        } ?: parameters["apkPath"]
        val apkFile = File(apkPath ?: return@post call.respond(HttpStatusCode.BadRequest))

        patcherService.patch(patchNames, multithreading, apkFile)

        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Route to sign the patched APK.
 */
private fun Route.configureSignRoute() {
    val patcherService = get<PatcherService>()

    post("/sign") {
        val signer: String by parameters
        val keyStorePassword = parameters["keyStorePassword"]
        val keyStoreEntryAlias: String by parameters
        val keyStoreEntryPassword: String by parameters

        patcherService.sign(signer, keyStorePassword, keyStoreEntryAlias, keyStoreEntryPassword)

        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Route to install or uninstall a patched APK.
 */
private fun Route.configureInstallationRoute() {
    val patcherService = get<PatcherService>()

    post("/install") {
        val mount = parameters["mount"]

        patcherService.install(mount)

        call.respond(HttpStatusCode.OK)
    }

    post("/uninstall") {
        val packageName: String by parameters
        val unmount = "unmount" in parameters

        patcherService.uninstall(packageName, unmount)

        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Route to delete temporary files produced by the patcher.
 */
private fun Route.configureCleanRoute() {
    val patcherService = get<PatcherService>()

    post("/clean") {
        patcherService.deleteTemporaryFiles()

        call.respond(HttpStatusCode.OK)
    }
}
