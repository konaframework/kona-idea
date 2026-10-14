package dev.kona.idea

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry.FileTypeDetector
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile

/** High-priority detector so Crema wins before HTML content sniffing. */
class CremaFileTypeDetector : FileTypeDetector {
    override fun detect(
        file: VirtualFile,
        firstBytes: ByteSequence,
        firstCharsIfText: CharSequence?,
    ): FileType? {
        if (CremaFileType.isCremaFileName(file.name)) {
            return CremaFileType.INSTANCE
        }
        return null
    }

    override fun getDesiredContentPrefixLength(): Int = 0
}
