package io.github.then00p.explodedhotswap.core;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What a folder of class files says about how it was compiled: the class file version, and for
 * classes with lambdas, which compiler built them. javac names a lambda's method after its
 * enclosing method (lambda$render$0), the Eclipse compiler numbers them (lambda$0). HotSwap
 * replaces a class only with the same set of methods, so an editor running the Eclipse compiler
 * cannot replace a class javac built if it has a lambda: "delete method not implemented".
 */
public final class ClassFiles {

  private static final int MAGIC = 0xCAFEBABE;
  private static final Pattern JAVAC_LAMBDA = Pattern.compile("lambda\\$[^$]+\\$\\d+");
  private static final Pattern ECJ_LAMBDA = Pattern.compile("lambda\\$\\d+");

  /** Which compiler built a class, as far as its lambdas tell. */
  public enum Compiler {
    JAVAC, ECJ, UNKNOWN
  }

  /** One class file's version and compiler. */
  public static final class Info {
    public final String path;
    public final int major;
    public final Compiler compiler;

    Info(String path, int major, Compiler compiler) {
      this.path = path;
      this.major = major;
      this.compiler = compiler;
    }
  }

  private ClassFiles() {
  }

  /** Every class file under the folder, with its path relative to it; files that are not classes are skipped. */
  public static List<Info> scan(File folder) {
    List<Info> infos = new ArrayList<>();
    if (!folder.isDirectory()) {
      return infos;
    }
    Path root = folder.toPath();
    try (Stream<Path> files = Files.walk(root)) {
      files.filter(Files::isRegularFile)
        .filter(file -> file.getFileName().toString().endsWith(".class"))
        .forEach(file -> {
          Info info = read(file, root.relativize(file).toString().replace(File.separatorChar, '/'));
          if (info != null) {
            infos.add(info);
          }
        });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return infos;
  }

  /** A class file's version and compiler, or null when it is not a class file. */
  public static Info read(Path file, String path) {
    try (InputStream in = Files.newInputStream(file)) {
      return read(in, path);
    } catch (IOException e) {
      return null;
    }
  }

  static Info read(InputStream stream, String path) throws IOException {
    DataInputStream in = new DataInputStream(stream);
    if (in.readInt() != MAGIC) {
      return null;
    }
    in.readUnsignedShort(); // minor
    int major = in.readUnsignedShort();
    int count = in.readUnsignedShort();
    boolean javac = false;
    boolean ecj = false;
    for (int i = 1; i < count; i++) {
      int tag = in.readUnsignedByte();
      switch (tag) {
        case 1: // Utf8
          String text = in.readUTF();
          javac |= JAVAC_LAMBDA.matcher(text).matches();
          ecj |= ECJ_LAMBDA.matcher(text).matches();
          break;
        case 7: case 8: case 16: case 19: case 20: // Class, String, MethodType, Module, Package
          in.skipBytes(2);
          break;
        case 15: // MethodHandle
          in.skipBytes(3);
          break;
        case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
          in.skipBytes(4);
          break;
        case 5: case 6: // Long, Double take two slots
          in.skipBytes(8);
          i++;
          break;
        default:
          throw new IOException("unknown constant pool tag " + tag + " in " + path);
      }
    }
    return new Info(path, major, javac ? Compiler.JAVAC : ecj ? Compiler.ECJ : Compiler.UNKNOWN);
  }

  /** "Java 17" for 61. */
  public static String javaVersion(int major) {
    return major >= 53 ? "Java " + (major - 44) : "Java 1." + (major - 44);
  }

  /** The class file version for a release as a build names it: "11", "1.8", "8". */
  public static int major(String release) {
    String version = release.trim();
    if (version.startsWith("1.")) {
      version = version.substring(2);
    }
    return Integer.parseInt(version) + 44;
  }
}
