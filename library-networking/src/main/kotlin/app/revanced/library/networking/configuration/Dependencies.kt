package app.revanced.library.networking.configuration

import app.revanced.library.networking.Server
import app.revanced.library.networking.services.HttpClientService
import app.revanced.library.networking.services.PatchBundleService
import app.revanced.library.networking.services.PatcherService
import io.ktor.server.application.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/**
 * Configure the dependencies for the application.
 *
 * @param dependenciesConfiguration The dependencies configuration.
 */
internal fun Application.configureDependencies(
    dependenciesConfiguration: Server.DependenciesConfiguration,
) {
    val globalModule = module {
        single { dependenciesConfiguration.storageRepository }
        single { dependenciesConfiguration.patchSetRepository }
        single { dependenciesConfiguration.appRepository }
        single { dependenciesConfiguration.installerRepository }
    }

    val patchBundleModule = module {
        single { HttpClientService() }
        singleOf(::PatchBundleService)
    }

    val patcherModule = module {
        singleOf(::PatcherService)
    }

    install(Koin) {
        modules(
            globalModule,
            patchBundleModule,
            patcherModule,
        )
    }
}
