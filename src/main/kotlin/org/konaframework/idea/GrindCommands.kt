package org.konaframework.idea

/**
 * Built-in `./grind` commands (aligned with kona `ListCommand.BUILTIN`).
 * Used for run-config presets and completion-style help — not a live process probe.
 */
object GrindCommands {
    data class Command(val name: String, val summary: String, val presetArgs: String = name)

    val BUILTIN: List<Command> =
        listOf(
            Command("serve", "Run the HTTP server (Netty + virtual threads, default port 4000)", "serve"),
            Command("make:command", "Generate a console command"),
            Command("make:controller", "Generate a controller (--resource for CRUD stubs)"),
            Command("make:resource", "Generate an API resource (--collection)"),
            Command("make:middleware", "Generate HTTP middleware"),
            Command("make:policy", "Generate an authorization policy"),
            Command("make:request", "Generate a form request"),
            Command("make:view", "Generate a Crema view"),
            Command("make:model", "Generate a Cadence model"),
            Command("make:migration", "Generate a migration"),
            Command("make:factory", "Generate a factory"),
            Command("make:seeder", "Generate a seeder"),
            Command("migrate", "Run pending migrations"),
            Command("migrate:fresh", "Drop all tables and re-run migrations (--seed)"),
            Command("migrate:refresh", "Reset and re-run migrations (--seed)"),
            Command("migrate:rollback", "Rollback migrations"),
            Command("migrate:reset", "Reset all migrations"),
            Command("migrate:install", "Create the migration bookkeeping table"),
            Command("migrate:status", "Show migration status"),
            Command("db:wipe", "Drop all tables (--drop-views)"),
            Command("db:seed", "Run database seeders"),
            Command("db:show", "Show connection info and tables"),
            Command("db:table", "Show columns for a table"),
            Command("schema:dump", "Write CREATE TABLE SQL to database/schema.sql"),
            Command("queue:work", "Process queued jobs"),
            Command("queue:failed", "List failed jobs"),
            Command("queue:retry", "Forget/retry failed jobs"),
            Command("queue:flush", "Clear failed jobs"),
            Command("schedule:run", "Run due scheduled tasks"),
            Command("schedule:list", "List scheduled tasks"),
            Command("schedule:work", "Run the scheduler in a loop"),
            Command("steam", "Interactive REPL"),
            Command("assets:vite", "Print Vite asset bundling setup help"),
            Command("storage:link", "Symlink public/storage to storage/app/public"),
            Command("key:generate", "Set the application key (APP_KEY)"),
            Command("list", "List grind commands"),
            Command("help", "Show usage"),
        )

    val PRESET_CONFIGS: List<Command> =
        listOf(
            BUILTIN.first { it.name == "serve" },
            BUILTIN.first { it.name == "list" },
            BUILTIN.first { it.name == "migrate" },
            BUILTIN.first { it.name == "queue:work" },
            BUILTIN.first { it.name == "schedule:work" },
        )

    fun find(name: String): Command? = BUILTIN.find { it.name == name }

    fun names(): List<String> = BUILTIN.map { it.name }
}
