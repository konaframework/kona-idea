package dev.kona.idea

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class CremaFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, CremaLanguage) {
    override fun getFileType(): FileType = CremaFileType.INSTANCE

    override fun toString(): String = "CremaFile"
}
