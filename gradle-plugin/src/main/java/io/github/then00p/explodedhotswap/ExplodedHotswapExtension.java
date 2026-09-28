package io.github.then00p.explodedhotswap;

import org.gradle.api.file.DirectoryProperty;

/**
 * explodedHotswap { editorOutput = layout.projectDirectory.dir("bin/main") }
 */
public abstract class ExplodedHotswapExtension {

  /**
   * Where the editor compiles the main source set: bin/main for VS Code, Eclipse and every other
   * editor built on the Eclipse Java language server (Neovim, Zed, ...) with a Gradle project.
   */
  public abstract DirectoryProperty getEditorOutput();
}
