package io.github.then00p.explodedhotswap;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;

import io.github.then00p.explodedhotswap.core.ClassFiles;
import io.github.then00p.explodedhotswap.core.EditorOutput;

/**
 * explodedWar: the WAR unpacked into build/exploded/&lt;name&gt;.war on every build, for an IDE's
 * server tooling to deploy as a folder, with the classes the editor compiled when they are current
 * so that HotSwap works. Everything else (resources, libraries, the webapp folder, whatever the
 * war task is configured with) is the WAR's own.
 */
public class ExplodedHotswapPlugin implements Plugin<Project> {

  @Override public void apply(Project project) {
    ExplodedHotswapExtension extension = project.getExtensions().create("explodedHotswap", ExplodedHotswapExtension.class);
    extension.getEditorOutput().convention(project.getLayout().getProjectDirectory().dir("bin/main"));
    project.getPlugins().withType(WarPlugin.class, war -> register(project, extension));
  }

  private void register(Project project, ExplodedHotswapExtension extension) {
    TaskProvider<War> war = project.getTasks().named(WarPlugin.WAR_TASK_NAME, War.class);
    SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
      .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    FileCollection sources = project.files(project.provider(() -> main.getJava().getSrcDirs()));
    Provider<File> javacClasses = main.getJava().getClassesDirectory().map(org.gradle.api.file.Directory::getAsFile);
    Decision decision = new Decision(extension.getEditorOutput().getAsFile(), javacClasses, sources);

    TaskProvider<Sync> exploded = project.getTasks().register("explodedWar", Sync.class, task -> {
      task.setGroup("build");
      task.setDescription("Unpacks the WAR into build/exploded for an IDE server to deploy, with the editor's "
        + "classes when they are current, so HotSwap works.");
      // The editor's classes go first and win; the WAR's own files fill in everything else.
      task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
      task.into("WEB-INF/classes", classes -> classes.from(project.provider(decision::classes)).include("**/*.class"));
      task.with(war.get());
      task.into(project.getLayout().getBuildDirectory().dir(war.flatMap(w -> w.getArchiveFileName())
        .map(name -> "exploded/" + name)));
      task.getInputs().files(sources).withPropertyName("sources");
      task.doFirst(t -> {
        decision.checkVersions();
        t.getLogger().lifecycle(decision.describe());
      });
    });
    project.getTasks().named("assemble", assemble -> assemble.dependsOn(exploded));
  }

  /** Decides once per build whether the editor's classes are current, and checks their version. */
  static final class Decision {
    private final Provider<File> editorOutput;
    private final Provider<File> javacClasses;
    private final FileCollection sources;
    private Optional<String> stale;

    Decision(Provider<File> editorOutput, Provider<File> javacClasses, FileCollection sources) {
      this.editorOutput = editorOutput;
      this.javacClasses = javacClasses;
      this.sources = sources;
    }

    private synchronized Optional<String> stale() {
      if (stale == null) {
        stale = EditorOutput.stale(editorOutput.get(), sources.getFiles());
      }
      return stale;
    }

    List<File> classes() {
      return stale().isPresent() ? List.of() : List.of(editorOutput.get());
    }

    /**
     * The editor's classes must not need a newer Java than javac's builds of the same classes: an
     * editor that reads the target level differently (Buildship takes it from the toolchain and
     * ignores options.release) would deploy classes the server cannot load.
     */
    void checkVersions() {
      if (stale().isPresent()) {
        return;
      }
      Map<String, Integer> javac = ClassFiles.scan(javacClasses.get()).stream()
        .collect(Collectors.toMap(info -> info.path, info -> info.major, Math::max));
      ClassFiles.scan(editorOutput.get()).stream()
        .filter(info -> javac.containsKey(info.path) && info.major > javac.get(info.path))
        .findFirst()
        .ifPresent(info -> {
          throw new GradleException("explodedWar: the editor compiled " + info.path + " for "
            + ClassFiles.javaVersion(info.major) + ", but the build targets " + ClassFiles.javaVersion(javac.get(info.path))
            + ". Set the Java level the editor reads, java { toolchain { languageVersion = ... } } or"
            + " sourceCompatibility/targetCompatibility, to the release you build for; the editor ignores"
            + " options.release. Until then HotSwap would deploy classes the server cannot load.");
        });
    }

    String describe() {
      return stale()
        .map(reason -> "explodedWar: javac's classes, since " + reason + ". HotSwap from the editor fails for"
          + " classes with lambdas until the editor has built the project.")
        .orElse("explodedWar: the editor's classes from " + editorOutput.get() + ", ready for HotSwap.");
    }
  }
}
