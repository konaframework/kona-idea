package dev.kona.idea

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Crema templates (`*.crema.html`, `*.crema.kt`).
 *
 * Compound extensions need an overrider + detector so HTML does not claim the file.
 */
class CremaFileType private constructor() :
    LanguageFileType(CremaLanguage),
    FileTypeIdentifiableByVirtualFile {
    override fun getName(): String = "Crema"

    override fun getDescription(): String = "Kona Crema template (.crema.html / .crema.kt)"

    override fun getDefaultExtension(): String = "crema.html"

    override fun getIcon(): Icon = KonaIcons.KONA

    override fun isMyFileType(file: VirtualFile): Boolean =
        !file.isDirectory && isCremaFileName(file.name)

    companion object {
        @JvmField
        val INSTANCE = CremaFileType()

        fun isCremaFileName(name: String): Boolean =
            name.endsWith(".crema.html", ignoreCase = true) ||
                name.endsWith(".crema.kt", ignoreCase = true)
    }
}
