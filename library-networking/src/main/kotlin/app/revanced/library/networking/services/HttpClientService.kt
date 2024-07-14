package app.revanced.library.networking.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File

/**
 * Service for HTTP client.
 */
internal class HttpClientService {
    private val client by lazy { HttpClient(CIO) }

    /**
     * Download a file from a URL to a file.
     *
     * @param file The file to download to.
     * @param url The URL to download from.
     */
    internal suspend fun downloadToFile(file: File, url: String) {
        client.get(url).body<ByteReadChannel>().copyTo(file.outputStream())
    }
}
