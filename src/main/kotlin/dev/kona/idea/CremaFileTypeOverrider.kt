package dev.kona.idea

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/** Force Crema filenames onto the Crema file type before HTML wins. */
class CremaFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (CremaFileType.isCremaFileName(file.name)) {
            return CremaFileType.INSTANCE
        }
        return null
    }
}
