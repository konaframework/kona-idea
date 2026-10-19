package org.konaframework.idea

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Official Kona mug mark (from monorepo kona-logo.svg). */
object KonaIcons {
    val KONA: Icon = IconLoader.getIcon("/icons/kona.svg", KonaIcons::class.java)
    val KONA_13: Icon = IconLoader.getIcon("/icons/kona_13.svg", KonaIcons::class.java)
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/konaToolWindow.svg", KonaIcons::class.java)
}
