package dev.kona.idea

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Where DB/model column suggestions come from. */
enum class ColumnSourceMode {
    /** Live JDBC via project `.env` (`DB_URL`, …) only. */
    CONNECTION_ONLY,

    /** Cadence migration parsing only. */
    MIGRATIONS_ONLY,

    /** Merge migrations + JDBC (default). */
    BOTH,
    ;

    companion object {
        fun fromStored(value: String?): ColumnSourceMode =
            entries.firstOrNull { it.name == value } ?: BOTH
    }
}

data class KonaIdeaState(
    var columnSourceMode: String = ColumnSourceMode.BOTH.name,
)

/**
 * Project-level Kona Idea settings (persisted in `.idea/konaIdea.xml`).
 */
@Service(Service.Level.PROJECT)
@State(name = "KonaIdeaSettings", storages = [Storage("konaIdea.xml")])
class KonaIdeaSettings : PersistentStateComponent<KonaIdeaState> {
    private var state = KonaIdeaState()

    var columnSourceMode: ColumnSourceMode
        get() = ColumnSourceMode.fromStored(state.columnSourceMode)
        set(value) {
            state.columnSourceMode = value.name
        }

    override fun getState(): KonaIdeaState = state

    override fun loadState(state: KonaIdeaState) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): KonaIdeaSettings = project.service()
    }
}
