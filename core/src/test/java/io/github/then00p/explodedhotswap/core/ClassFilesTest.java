package io.github.then00p.explodedhotswap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.ToolProvider;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFilesTest {

  @TempDir Path dir;

  private Path source(String name, String code) throws IOException {
    Path file = dir.resolve("src/demo/" + name + ".java");
    Files.createDirectories(file.getParent());
    Files.write(file, code.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  private Path javac(Path source, String release) {
    Path out = dir.resolve("javac");
    int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null,
      "--release", release, "-d", out.toString(), source.toString());
    assertEquals(0, exit);
    return out;
  }

  private Path ecj(Path source, String release) {
    Path out = dir.resolve("ecj");
    StringWriter errors = new StringWriter();
    boolean ok = BatchCompiler.compile(new String[] {"--release", release, "-proc:none", "-nowarn",
      "-d", out.toString(), source.toString()}, new PrintWriter(new StringWriter()), new PrintWriter(errors), null);
    assertTrue(ok, errors.toString());
    return out;
  }

  private static final String WITH_LAMBDA = "package demo;\npublic class Hello {\n"
    + "  public Runnable greet() { return () -> System.out.println(\"hi\"); }\n}\n";

  @Test void javacLambdasAreRecognised() throws IOException {
    List<ClassFiles.Info> infos = ClassFiles.scan(javac(source("Hello", WITH_LAMBDA), "11").toFile());
    assertEquals(1, infos.size());
    assertEquals("demo/Hello.class", infos.get(0).path);
    assertEquals(55, infos.get(0).major);
    assertEquals(ClassFiles.Compiler.JAVAC, infos.get(0).compiler);
  }

  @Test void eclipseCompilerLambdasAreRecognised() throws IOException {
    List<ClassFiles.Info> infos = ClassFiles.scan(ecj(source("Hello", WITH_LAMBDA), "11").toFile());
    assertEquals(55, infos.get(0).major);
    assertEquals(ClassFiles.Compiler.ECJ, infos.get(0).compiler);
  }

  @Test void theClassFileVersionFollowsTheRelease() throws IOException {
    Path source = source("Hello", WITH_LAMBDA);
    assertEquals(61, ClassFiles.scan(javac(source, "17").toFile()).get(0).major);
    assertEquals(61, ClassFiles.scan(ecj(source, "17").toFile()).get(0).major);
  }

  @Test void aClassWithoutLambdasDoesNotTell() throws IOException {
    Path source = source("Plain", "package demo;\npublic class Plain {\n  int x() { return 1; }\n}\n");
    assertEquals(ClassFiles.Compiler.UNKNOWN, ClassFiles.scan(javac(source, "11").toFile()).get(0).compiler);
    assertEquals(ClassFiles.Compiler.UNKNOWN, ClassFiles.scan(ecj(source, "11").toFile()).get(0).compiler);
  }

  @Test void filesThatAreNotClassesAreSkipped() throws IOException {
    Path fake = dir.resolve("fake/demo/Fake.class");
    Files.createDirectories(fake.getParent());
    Files.write(fake, "not a class".getBytes(StandardCharsets.UTF_8));
    assertNull(ClassFiles.read(fake, "demo/Fake.class"));
    assertTrue(ClassFiles.scan(dir.resolve("fake").toFile()).isEmpty());
  }

  @Test void releasesMapToClassFileVersions() {
    assertEquals(52, ClassFiles.major("1.8"));
    assertEquals(52, ClassFiles.major("8"));
    assertEquals(55, ClassFiles.major("11"));
    assertEquals("Java 17", ClassFiles.javaVersion(61));
    assertEquals("Java 1.8", ClassFiles.javaVersion(52));
  }
}
