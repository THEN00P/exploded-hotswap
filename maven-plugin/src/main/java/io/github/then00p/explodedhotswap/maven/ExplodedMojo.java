package io.github.then00p.explodedhotswap.maven;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import io.github.then00p.explodedhotswap.core.ClassFiles;
import io.github.then00p.explodedhotswap.core.EditorOutput;

/**
 * mvn exploded-hotswap:exploded: the WAR unpacked into target/exploded/&lt;finalName&gt;.war from target/classes
 * as they are, without compiling, for an IDE's server tooling to deploy as a folder. Your editor
 * and Maven share target/classes, and mvn compile, test or package recompile the whole module with
 * javac; an editor on the Eclipse compiler (VS Code, Eclipse, Neovim, Zed) cannot HotSwap over
 * javac's classes. So this runs instead of mvn package while you work, after the editor has built.
 * The folder itself is maven-war-plugin:exploded's, with the project's own war configuration.
 */
@Mojo(name = "exploded", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class ExplodedMojo extends AbstractMojo {

  private static final String WAR_PLUGIN = "org.apache.maven.plugins:maven-war-plugin";
  private static final String COMPILER_PLUGIN = "org.apache.maven.plugins:maven-compiler-plugin";

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Component
  private BuildPluginManager pluginManager;

  /**
   * The compiler your editor builds with: ecj, the Eclipse compiler and the default in VS Code,
   * Eclipse, Neovim and Zed, or javac, with VS Code's experimental java.jdt.ls.javac.enabled. With
   * javac, classes javac built are the editor's kind, so there is nothing to warn about.
   */
  @Parameter(property = "explodedHotswap.editorCompiler", defaultValue = "ecj")
  private String editorCompiler;

  /** The maven-war-plugin version to run when the project does not name one. */
  @Parameter(property = "explodedHotswap.warPluginVersion", defaultValue = "3.5.1")
  private String warPluginVersion;

  @Override public void execute() throws MojoExecutionException, MojoFailureException {
    File classes = new File(project.getBuild().getOutputDirectory());
    if (!classes.isDirectory()) {
      throw new MojoFailureException(classes + " does not exist: open the project in your editor and let it build,"
        + " or run mvn compile first");
    }
    List<ClassFiles.Info> infos = ClassFiles.scan(classes);
    checkVersions(classes, infos);

    List<ClassFiles.Info> javac = infos.stream()
      .filter(info -> info.compiler == ClassFiles.Compiler.JAVAC)
      .collect(Collectors.toList());
    if (!javac.isEmpty() && !"javac".equalsIgnoreCase(editorCompiler)) {
      getLog().warn(javac.size() + " class(es) in " + classes + " were compiled by javac, " + javac.get(0).path
        + " among them. HotSwap from an editor on the Eclipse compiler (VS Code, Eclipse, Neovim, Zed) fails for"
        + " them: mvn compile, test and package recompile the whole module with javac. Let the editor rebuild the"
        + " project (VS Code: \"Java: Rebuild Projects\"), then run this again.");
    }
    List<File> sourceRoots = project.getCompileSourceRoots().stream().map(File::new).collect(Collectors.toList());
    Optional<String> stale = EditorOutput.stale(classes, sourceRoots);
    stale.ifPresent(reason -> getLog().warn(reason + ": the folder gets the class as it is. Let the editor build, then run"
      + " this again."));

    Plugin war = project.getPlugin(WAR_PLUGIN);
    String version = war != null && war.getVersion() != null ? war.getVersion() : warPluginVersion;
    Xpp3Dom config = war != null && war.getConfiguration() instanceof Xpp3Dom
      ? new Xpp3Dom((Xpp3Dom) war.getConfiguration()) : configuration();
    // target/exploded/<finalName>.war, as the Gradle plugin writes it: WildFly only deploys an
    // exploded folder whose name ends in .war. A webappDirectory the project sets wins.
    Xpp3Dom folder = config.getChild("webappDirectory");
    if (folder == null) {
      folder = new Xpp3Dom("webappDirectory");
      folder.setValue(new File(project.getBuild().getDirectory(), "exploded/" + project.getBuild().getFinalName() + ".war")
        .getAbsolutePath());
      config.addChild(folder);
    }
    executeMojo(plugin(groupId("org.apache.maven.plugins"), artifactId("maven-war-plugin"), version(version)),
      goal("exploded"), config, executionEnvironment(project, session, pluginManager));
    getLog().info("exploded-hotswap: deploy " + folder.getValue()
      + ((javac.isEmpty() || "javac".equalsIgnoreCase(editorCompiler)) && stale.isEmpty() ? ", ready for HotSwap" : ""));
  }

  /**
   * No class may need a newer Java than the build targets: an editor that reads the target level
   * differently would put classes into the folder that the server cannot load.
   */
  private void checkVersions(File classes, List<ClassFiles.Info> infos) throws MojoFailureException {
    String release = targetRelease();
    if (release == null) {
      return;
    }
    int target;
    try {
      target = ClassFiles.major(release);
    } catch (NumberFormatException e) {
      return;
    }
    for (ClassFiles.Info info : infos) {
      if (info.major > target) {
        throw new MojoFailureException(info.path + " in " + classes + " was compiled for " + ClassFiles.javaVersion(info.major)
          + ", but the project targets " + ClassFiles.javaVersion(target) + ". Make the editor compile for the"
          + " project's release (it reads maven.compiler.release, or source and target) and let it rebuild; until then"
          + " the server could not load these classes.");
      }
    }
  }

  /** The release the project compiles for: maven-compiler-plugin's release or target, or the matching properties. */
  private String targetRelease() {
    Plugin compiler = project.getPlugin(COMPILER_PLUGIN);
    if (compiler != null && compiler.getConfiguration() instanceof Xpp3Dom) {
      Xpp3Dom config = (Xpp3Dom) compiler.getConfiguration();
      for (String name : new String[] {"release", "target"}) {
        Xpp3Dom child = config.getChild(name);
        if (child != null && child.getValue() != null && !child.getValue().contains("${")) {
          return child.getValue();
        }
      }
    }
    String release = project.getProperties().getProperty("maven.compiler.release");
    return release != null ? release : project.getProperties().getProperty("maven.compiler.target");
  }
}
