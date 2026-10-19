package org.konaframework.idea

/**
 * Pure helpers for composing `./grind` argv, including Netty serve bind options.
 */
object GrindArgs {
    const val DEFAULT_HOST: String = "127.0.0.1"
    const val DEFAULT_PORT: String = "4000"

    fun split(raw: String): List<String> =
        raw.split(Regex("\\s+")).filter { it.isNotBlank() }

    /**
     * When the command is `serve`, ensure `--host` / `--port` are present so the
     * Netty [KonaHttpServer] bind matches the run configuration (grind forwards these
     * as `HOST` / `PORT` to `./gradlew run`).
     */
    fun compose(rawArgs: String, host: String, port: String): List<String> {
        val parts = split(rawArgs).ifEmpty { listOf("list") }.toMutableList()
        if (parts.first() != "serve") {
            return parts
        }
        val hasHost = parts.any { it == "--host" || it.startsWith("--host=") }
        val hasPort = parts.any { it == "--port" || it.startsWith("--port=") }
        val h = host.ifBlank { DEFAULT_HOST }
        val p = port.ifBlank { DEFAULT_PORT }
        if (!hasHost) parts += "--host=$h"
        if (!hasPort) parts += "--port=$p"
        return parts
    }

    fun serveDescription(host: String = DEFAULT_HOST, port: String = DEFAULT_PORT): String =
        "Kona HTTP via Netty + virtual threads — http://$host:$port (./grind serve)"
}
