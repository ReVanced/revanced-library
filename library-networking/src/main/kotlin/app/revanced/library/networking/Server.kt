@file:Suppress("unused")

package app.revanced.library.networking

import app.revanced.library.installation.installer.AdbInstaller
import app.revanced.library.networking.configuration.configureDependencies
import app.revanced.library.networking.configuration.configureHTTP
import app.revanced.library.networking.configuration.configureSecurity
import app.revanced.library.networking.configuration.configureSerialization
import app.revanced.library.networking.configuration.repository.AppRepository
import app.revanced.library.networking.configuration.repository.InstallerRepository
import app.revanced.library.networking.configuration.repository.PatchSetRepository
import app.revanced.library.networking.configuration.repository.StorageRepository
import app.revanced.library.networking.configuration.routing.configureRouting
import app.revanced.library.networking.models.App
import app.revanced.library.networking.models.PatchBundle
import app.revanced.patcher.PatchBundleLoader
import app.revanced.patcher.PatchSet
import app.revanced.patcher.patch.options.PatchOption
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File
import java.time.LocalDateTime
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A server.
 *
 * @param host The host.
 * @param port The port.
 * @param engineFactory The engine factory.
 * @param securityConfiguration The security configuration.
 * @param dependenciesConfiguration The dependencies configuration.
 * @param serializersConfiguration The serializers configuration.
 */
class Server internal constructor(
    host: String,
    port: Int,
    engineFactory: ApplicationEngineFactory<*, *>,
    securityConfiguration: SecurityConfiguration,
    dependenciesConfiguration: DependenciesConfiguration,
    serializersConfiguration: SerializersConfiguration,
) {
    private val applicationEngine = embeddedServer(engineFactory, port, host) {
        configureHTTP(allowedHost = host)
        configureSecurity(securityConfiguration)
        configureDependencies(dependenciesConfiguration)
        configureSerialization(serializersConfiguration)
        configureRouting()
    }

    /**
     * Starts the server and blocks the current thread.
     */
    fun start() = applicationEngine.start(wait = true)

    /**
     * Stops the server.
     */
    fun stop() = applicationEngine.stop()

    /**
     * The security configuration.
     *
     * @property username The username.
     * @property password The password.
     */
    class SecurityConfiguration(
        internal val username: String,
        internal val password: String,
    )

    /**
     * The dependencies configuration.
     *
     * @property storageRepository The storage repository.
     * @property patchSetRepository The patch set repository.
     * @property appRepository The app repository.
     * @property installerRepository The installer repository.
     */
    class DependenciesConfiguration(
        internal val storageRepository: StorageRepository,
        internal val patchSetRepository: PatchSetRepository,
        internal val appRepository: AppRepository,
        internal val installerRepository: InstallerRepository,
    )

    /**
     * The serializers configuration.
     *
     * @property patchOptionValueTypes A map of [PatchOption.valueType] to [KType] to add serializers for patch options
     * additional to the default ones.
     */
    class SerializersConfiguration(
        internal val patchOptionValueTypes: Map<String, KType> = emptyMap(),
    )
}

/**
 * A server builder.
 *
 * @property host The host.
 * @property port The port.
 * @property engineFactory The engine factory.
 * @property securityConfiguration The security configuration.
 * @property dependenciesConfiguration The dependencies configuration.
 */
class ServerBuilder internal constructor(
    private val host: String = "localhost",
    private val port: Int = 8080,
    private val engineFactory: ApplicationEngineFactory<*, *> = Netty,
) {
    private lateinit var securityConfiguration: Server.SecurityConfiguration
    private lateinit var dependenciesConfiguration: Server.DependenciesConfiguration
    private var serializersConfiguration = Server.SerializersConfiguration()

    /**
     * Configures the security.
     *
     * @param basicUsername The basic username.
     * @param basicPassword The basic password.
     *
     * @return The server builder.
     */
    fun configureSecurity(
        basicUsername: String,
        basicPassword: String,
    ) = apply {
        securityConfiguration = Server.SecurityConfiguration(
            username = basicUsername,
            password = basicPassword,
        )
    }

    /**
     * Configures the dependencies.
     *
     * @param block The block to configure the dependencies.
     *
     * @return The server builder.
     */
    fun configureDependencies(block: DependenciesConfigurationBuilder.() -> Unit) = apply {
        dependenciesConfiguration = DependenciesConfigurationBuilder().apply(block).build()
    }

    /**
     * Configures the serializers.
     *
     * @param block The block to configure the serializers.
     *
     * @return The server builder.
     */
    fun configureSerializers(block: SerializersConfigurationBuilder.() -> Unit) = apply {
        serializersConfiguration = SerializersConfigurationBuilder().apply(block).build()
    }

    class DependenciesConfigurationBuilder internal constructor() {
        private lateinit var storageRepository: StorageRepository
        private lateinit var patchSetRepository: PatchSetRepository
        private lateinit var appRepository: AppRepository
        private lateinit var installerRepository: InstallerRepository

        fun configureStorageRepository(storageRepository: StorageRepository) = apply {
            this.storageRepository = storageRepository
        }

        fun configurePatchSetRepository(patchSetRepository: PatchSetRepository) = apply {
            this.patchSetRepository = patchSetRepository
        }

        fun configureAppRepository(appRepository: AppRepository) = apply {
            this.appRepository = appRepository
        }

        fun configureInstallerRepository(installerRepository: InstallerRepository) = apply {
            this.installerRepository = installerRepository
        }

        fun build() = Server.DependenciesConfiguration(
            storageRepository,
            patchSetRepository,
            appRepository,
            installerRepository,
        )
    }

    class SerializersConfigurationBuilder internal constructor() {
        private lateinit var patchOptionValueTypes: Map<String, KType>

        fun configurePatchOptionSerializers(vararg pairs: Pair<String, KType>) {
            this.patchOptionValueTypes = mapOf(*pairs)
        }

        fun build() = Server.SerializersConfiguration(patchOptionValueTypes)
    }

    /**
     * Builds the server.
     *
     * @return The server.
     */
    internal fun build() = Server(
        host,
        port,
        engineFactory,
        securityConfiguration,
        dependenciesConfiguration,
        serializersConfiguration,
    )
}

/**
 * Creates a server.
 *
 * @param host The host.
 * @param port The port.
 * @param engineFactory The engine factory.
 * @param block The block to build the server.
 *
 * @return The server.
 */
fun server(
    host: String = "localhost",
    port: Int = 8080,
    engineFactory: ApplicationEngineFactory<*, *> = Netty,
    block: ServerBuilder.() -> Unit = {},
) = ServerBuilder(host, port, engineFactory).apply(block).build()

fun main() {
    server {
        configureSecurity("username", "password")

        val storageRepository = object : StorageRepository(
            temporaryFilesPath = File("temp"),
            keystoreFilePath = File("keystore.jks"),
        ) {
            override fun readPatchBundles() = setOf(
                PatchBundle(
                    "ReVanced Patches",
                    File("D:\\ReVanced\\revanced-patches\\build\\libs\\revanced-patches-4.7.0-dev.2.jar"),
                ),
            )

            override fun writePatchBundles(patchBundles: Set<PatchBundle>) {
                // TODO("Not yet implemented")
            }

            override fun newPatchBundle(patchBundleName: String, withIntegrations: Boolean): PatchBundle {
                TODO("Not yet implemented")
            }
        }

        val patchSetRepository = object : PatchSetRepository(storageRepository) {
            override fun readPatchSet(patchBundles: Set<PatchBundle>): PatchSet {
                return PatchBundleLoader.Jar(*patchBundles.map { it.patchBundleFile }.toTypedArray())
            }
        }

        val appRepository = object : AppRepository() {
            override fun readInstalledApps() = emptySet<App>()
        }

        val installerRepository = object : InstallerRepository() {
            override val installer = AdbInstaller("127.0.0.1:58526")
        }

        configureDependencies {
            configureStorageRepository(storageRepository)
            configurePatchSetRepository(patchSetRepository)
            configureAppRepository(appRepository)
            configureInstallerRepository(installerRepository)
        }

        configureSerializers {
            configurePatchOptionSerializers(
                "LocalDateTime" to typeOf<PatchOption<LocalDateTime>>(),
            )
        }
    }.start()
}
