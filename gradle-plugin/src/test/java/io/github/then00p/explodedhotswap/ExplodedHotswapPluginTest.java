package io.github.then00p.explodedhotswap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExplodedHotswapPluginTest {

  @TempDir Path project;

  static Stream<String> gradleVersions() {
    return Stream.of(System.getProperty("gradle.versions", "9.8.0").split(","));
  }

  private void write(String path, String text) throws IOException {
    Path file = project.resolve(path);
    Files.createDirectories(file.getParent());
    Files.write(file, text.getBytes(StandardCharsets.UTF_8));
  }

  private String read(String path) throws IOException {
    return new String(Files.readAllBytes(project.resolve(path)), StandardCharsets.ISO_8859_1);
  }

  private void setUp() throws IOException {
    write("settings.gradle", "rootProject.name = 'demo'\n");
    write("build.gradle", "plugins {\n  id 'war'\n  id 'io.github.then00p.exploded-hotswap'\n}\n"
      + "tasks.withType(JavaCompile).configureEach { options.release = 11 }\n");
    write("src/main/java/demo/Hello.java", "package demo;\n\npublic class Hello {\n"
      + "  public Runnable greet() {\n    return () -> System.out.println(\"hello\");\n  }\n}\n");
    write("src/main/java/demo/package-info.java", "package demo;\n");
    write("src/main/resources/app.properties", "from=gradle\n");
    write("src/main/webapp/index.html", "<p>hi</p>\n");
  }

  private GradleRunner runner(String gradle) {
    return GradleRunner.create()
      .withProjectDir(project.toFile())
      .withPluginClasspath()
      .withGradleVersion(gradle)
      .withArguments("build", "--stacktrace");
  }

  /** What an editor on the Eclipse compiler writes: the class, and a copied resource. */
  private void editorBuild(String release) throws IOException {
    StringWriter errors = new StringWriter();
    boolean ok = BatchCompiler.compile(new String[] {"--release", release, "-proc:none", "-nowarn",
      "-d", project.resolve("bin/main").toString(), project.resolve("src/main/java/demo/Hello.java").toString()},
      new PrintWriter(new StringWriter()), new PrintWriter(errors), null);
    assertTrue(ok, errors.toString());
    write("bin/main/app.properties", "from=editor\n");
  }

  private static final String CLASS = "build/exploded/demo.war/WEB-INF/classes/demo/Hello.class";

  @ParameterizedTest @MethodSource("gradleVersions")
  void withoutAnEditorBuildTheWarsOwnClassesAreUsed(String gradle) throws IOException {
    setUp();
    BuildResult result = runner(gradle).build();
    assertTrue(result.getOutput().contains("explodedWar: javac's classes"), result.getOutput());
    assertTrue(read(CLASS).contains("lambda$greet$0"), "javac's lambda naming");
    assertEquals("<p>hi</p>\n", read("build/exploded/demo.war/index.html"));
  }

  @ParameterizedTest @MethodSource("gradleVersions")
  void aCurrentEditorBuildWinsForClassesButNotForResources(String gradle) throws IOException {
    setUp();
    editorBuild("11");
    BuildResult result = runner(gradle).build();
    assertTrue(result.getOutput().contains("explodedWar: the editor's classes"), result.getOutput());
    assertTrue(read(CLASS).contains("lambda$0") && !read(CLASS).contains("lambda$greet$0"), "the Eclipse compiler's class");
    assertEquals("from=gradle\n", read("build/exploded/demo.war/WEB-INF/classes/app.properties"));
  }

  @ParameterizedTest @MethodSource("gradleVersions")
  void aSourceNewerThanTheEditorsClassFallsBackToJavac(String gradle) throws IOException {
    setUp();
    editorBuild("11");
    Files.setLastModifiedTime(project.resolve("bin/main/demo/Hello.class"),
      FileTime.fromMillis(Files.getLastModifiedTime(project.resolve("src/main/java/demo/Hello.java")).toMillis() - 60_000));
    BuildResult result = runner(gradle).build();
    assertTrue(result.getOutput().contains("demo" + java.io.File.separator + "Hello.java is newer than its class"),
      result.getOutput());
    assertTrue(read(CLASS).contains("lambda$greet$0"));
  }

  @ParameterizedTest @MethodSource("gradleVersions")
  void switchingBackToTheEditorsClassesReplacesJavacs(String gradle) throws IOException {
    setUp();
    runner(gradle).build();
    assertTrue(read(CLASS).contains("lambda$greet$0"));
    editorBuild("11");
    runner(gradle).build();
    assertTrue(read(CLASS).contains("lambda$0"));
  }

  @ParameterizedTest @MethodSource("gradleVersions")
  void anEditorBuildingForANewerJavaFailsTheBuild(String gradle) throws IOException {
    setUp();
    editorBuild("17");
    BuildResult result = runner(gradle).buildAndFail();
    assertTrue(result.getOutput().contains("the editor compiled demo/Hello.class for Java 17, but the build targets Java 11"),
      result.getOutput());
  }
}
