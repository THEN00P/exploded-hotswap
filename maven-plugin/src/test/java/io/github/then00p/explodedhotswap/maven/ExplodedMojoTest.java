package io.github.then00p.explodedhotswap.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs a real Maven (mvn on the PATH, or MVN) against a WAR project and the plugin in the local repository. */
class ExplodedMojoTest {

  @TempDir Path project;

  private static final String CLASS = "target/exploded/demo.war/WEB-INF/classes/demo/Hello.class";

  @BeforeEach void setUp() throws IOException {
    write("pom.xml", "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>demo</groupId><artifactId>demo</artifactId><version>1</version><packaging>war</packaging>\n"
      + "  <properties>\n"
      + "    <maven.compiler.release>11</maven.compiler.release>\n"
      + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
      + "  </properties>\n"
      + "  <build>\n"
      + "    <finalName>demo</finalName>\n"
      + "    <plugins>\n"
      + "      <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-war-plugin</artifactId>"
      + "<version>3.4.0</version><configuration><failOnMissingWebXml>false</failOnMissingWebXml></configuration></plugin>\n"
      + "      <plugin><groupId>io.github.then00p</groupId><artifactId>exploded-hotswap-maven-plugin</artifactId>"
      + "<version>0.1.0-SNAPSHOT</version></plugin>\n"
      + "    </plugins>\n"
      + "  </build>\n"
      + "</project>\n");
    write("src/main/java/demo/Hello.java", "package demo;\n\npublic class Hello {\n"
      + "  public Runnable greet() {\n    return () -> System.out.println(\"hello\");\n  }\n}\n");
    write("src/main/webapp/index.html", "<p>hi</p>\n");
  }

  private void write(String path, String text) throws IOException {
    Path file = project.resolve(path);
    Files.createDirectories(file.getParent());
    Files.write(file, text.getBytes(StandardCharsets.UTF_8));
  }

  private String read(String path) throws IOException {
    return new String(Files.readAllBytes(project.resolve(path)), StandardCharsets.ISO_8859_1);
  }

  /** mvn with the arguments; the output, with the exit code on its first line. */
  private String mvn(String... args) throws IOException, InterruptedException {
    String mvn = System.getenv().getOrDefault("MVN",
      System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn");
    List<String> command = new ArrayList<>(List.of(mvn, "-B"));
    command.addAll(List.of(args));
    Process process = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return process.waitFor() + "\n" + output;
  }

  /** What an editor on the Eclipse compiler writes into target/classes. */
  private void editorBuild(String release) {
    StringWriter errors = new StringWriter();
    boolean ok = BatchCompiler.compile(new String[] {"--release", release, "-proc:none", "-nowarn",
      "-d", project.resolve("target/classes").toString(), project.resolve("src/main/java/demo/Hello.java").toString()},
      new PrintWriter(new StringWriter()), new PrintWriter(errors), null);
    assertTrue(ok, errors.toString());
  }

  @Test void javacClassesAreDeployedWithAWarning() throws Exception {
    String output = mvn("compile", "exploded-hotswap:exploded");
    assertTrue(output.startsWith("0\n"), output);
    assertTrue(output.contains("were compiled by javac, demo/Hello.class among them"), output);
    assertTrue(read(CLASS).contains("lambda$greet$0"));
    assertEquals("<p>hi</p>\n", read("target/exploded/demo.war/index.html"));
  }

  @Test void anEditorOnJavacMakesJavacClassesTheRightKind() throws Exception {
    String output = mvn("compile", "exploded-hotswap:exploded", "-DexplodedHotswap.editorCompiler=javac");
    assertTrue(output.startsWith("0\n"), output);
    assertFalse(output.contains("compiled by javac"), output);
    assertTrue(output.contains("ready for HotSwap"), output);
  }

  @Test void theEditorsClassesAreDeployedAsTheyAreWithoutCompiling() throws Exception {
    editorBuild("11");
    String output = mvn("exploded-hotswap:exploded");
    assertTrue(output.startsWith("0\n"), output);
    assertFalse(output.contains("compiled by javac"), output);
    assertTrue(output.contains("ready for HotSwap"), output);
    assertTrue(read(CLASS).contains("lambda$0"), "the Eclipse compiler's class");
  }

  @Test void classesForANewerJavaFailTheGoal() throws Exception {
    editorBuild("17");
    String output = mvn("exploded-hotswap:exploded");
    assertFalse(output.startsWith("0\n"), output);
    assertTrue(output.contains("demo/Hello.class in ") && output.contains("was compiled for Java 17, but the project targets Java 11"),
      output);
  }
}
