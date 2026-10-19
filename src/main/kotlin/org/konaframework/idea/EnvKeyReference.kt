package org.konaframework.idea

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import java.nio.file.Files

/** Ctrl+Click / Ctrl+B from an `Env.get` key to that assignment in `.env`. */
class EnvKeyReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val key: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, false) {

    override fun resolve(): PsiElement? = resolveEnvKey(element, key)

    override fun getVariants(): Array<Any> = emptyArray()
}

internal fun resolveEnvKey(element: PsiElement, key: String): PsiElement? {
    val project = element.project
    val root = project.guessProjectDir()?.toNioPath() ?: return null
    val envPath = EnvKeys.envFile(root)
    if (!Files.isRegularFile(envPath)) return null
    val content = runCatching { Files.readString(envPath) }.getOrNull() ?: return null
    val decl = EnvKeys.declaration(content, key) ?: return null
    val vf = LocalFileSystem.getInstance().findFileByNioFile(envPath) ?: return null
    val psi = PsiManager.getInstance(project).findFile(vf) ?: return null
    return psi.findElementAt(decl.start) ?: psi
}
