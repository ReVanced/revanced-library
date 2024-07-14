package app.revanced.library.networking

import io.ktor.server.application.*
import io.ktor.util.pipeline.*

internal val PipelineContext<*, ApplicationCall>.parameters get() = call.parameters
