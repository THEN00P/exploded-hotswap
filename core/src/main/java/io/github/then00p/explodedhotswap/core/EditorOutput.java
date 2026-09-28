package io.github.then00p.explodedhotswap.core;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Whether classes are current for their sources. HotSwap from an editor sends classes its
 * compiler built, and the JVM only accepts them over classes from the same compiler (see
 * {@link ClassFiles}). One rule decides: every source has a class at least as new as itself.
 */
public final class EditorOutput {

  private EditorOutput() {
  }

  /** Why the classes are not current, or empty when they are. */
  public static Optional<String> stale(File classes, Iterable<File> sourceDirs) {
    if (!classes.isDirectory()) {
      return Optional.of(classes + " does not exist; no editor has built this project");
    }
    for (File sourceDir : sourceDirs) {
      if (!sourceDir.isDirectory()) {
        continue;
      }
      Path root = sourceDir.toPath();
      try (Stream<Path> files = Files.walk(root)) {
        Optional<String> reason = files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".java"))
          .map(file -> staleClass(root, file, classes.toPath()))
          .flatMap(Optional::stream)
          .findFirst();
        if (reason.isPresent()) {
          return reason;
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> staleClass(Path root, Path source, Path classes) {
    String name = source.getFileName().toString();
    // Only an annotated package-info compiles to a class; module-info is not deployed.
    if (name.equals("package-info.java") || name.equals("module-info.java")) {
      return Optional.empty();
    }
    String relative = root.relativize(source).toString();
    Path compiled = classes.resolve(relative.substring(0, relative.length() - ".java".length()) + ".class");
    try {
      if (!Files.isRegularFile(compiled)) {
        return Optional.of(relative + " has no class in " + classes);
      }
      if (Files.getLastModifiedTime(compiled).compareTo(Files.getLastModifiedTime(source)) < 0) {
        return Optional.of(relative + " is newer than its class in " + classes);
      }
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
