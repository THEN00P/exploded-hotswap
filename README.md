# exploded-hotswap

Gradle and Maven plugins that deploy your editor's own classes, so HotSwap works from VS Code, Neovim, Zed and other editors on the Eclipse Java language server.

## Setup in VS Code

You need these VS Code extensions: Language Support for Java (Red Hat), Debugger for Java, Community Server Connectors (Runtime Server Protocol), and Gradle for Java if you use Gradle.

In your user `settings.json`, turn on HotSwap on save:

```json
"java.debug.settings.hotCodeReplace": "auto"
```

The plugins aren't published yet, so install them into your local Maven repository once:

```sh
git clone https://github.com/THEN00P/exploded-hotswap.git
cd exploded-hotswap
./gradlew publishToMavenLocal
```

### Gradle

`settings.gradle.kts`:

```kotlin
pluginManagement {
  repositories {
    mavenLocal()
    gradlePluginPortal()
  }
}
```

`build.gradle.kts`:

```kotlin
plugins {
  war
  id("io.github.then00p.exploded-hotswap") version "0.1.0-SNAPSHOT"
}

java {
  // The Java version your server runs. The editor reads it from here, not from options.release.
  toolchain {
    languageVersion = JavaLanguageVersion.of(11)
  }
}
```

Then:

1. Open the project in VS Code and wait until the Java status in the status bar says it's done building.
2. Run `./gradlew build`. The output ends with `explodedWar: the editor's classes from .../bin/main, ready for HotSwap.`
3. In the Servers view, start your server, then Add Deployment and pick the folder `build/exploded/<name>.war`. Publish it (full publish).
4. Right-click the server and start it in debug mode.
5. Edit a Java file and save. VS Code compiles it and hot-swaps it into the running server.

Run `./gradlew build` and a full publish again when you add or change dependencies, or when you added a method, a field or changed a signature. HotSwap can't do those. If the build says `javac's classes` instead of `the editor's classes`, run "Java: Rebuild Projects" from the command palette first.

### Maven

`pom.xml`, inside `<build><plugins>`:

```xml
<plugin>
  <groupId>io.github.then00p</groupId>
  <artifactId>exploded-hotswap-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</plugin>
```

Set the Java version your server runs, for example `<maven.compiler.release>11</maven.compiler.release>` in `<properties>`.

Then:

1. Open the project in VS Code and run "Java: Rebuild Projects" from the command palette, so every class in `target/classes` is VS Code's own build, even if you ran `mvn` before.
2. Run `mvn exploded-hotswap:exploded`. Not `mvn package`, see below. The output ends with `exploded-hotswap: deploy .../target/exploded/<finalName>.war, ready for HotSwap`.
3. In the Servers view, start your server, then Add Deployment and pick the folder `target/exploded/<finalName>.war`. Publish it (full publish).
4. Right-click the server and start it in debug mode.
5. Edit a Java file and save. VS Code compiles it and hot-swaps it into the running server.

Run `mvn exploded-hotswap:exploded` and a full publish again for the same reasons as with Gradle. If it warns that classes `were compiled by javac`, you ran `mvn compile`, `test` or `package` since VS Code last built. Run "Java: Rebuild Projects", then the goal again.

## Why HotSwap fails without this

VS Code compiles your code with the Eclipse compiler. So do Neovim, Zed and every other editor built on the Eclipse Java language server. HotSwap sends those classes to the server, but the server runs the classes Gradle or Maven built with javac.

The two compilers name the methods behind lambdas differently. javac writes `lambda$render$0`, the Eclipse compiler writes `lambda$0`. The JVM can only swap method bodies, so it reads the new class as one method removed and another added, and rejects it with `delete method not implemented` or `add method not implemented`. That happens for every class with a lambda, whatever line you changed.

Eclipse itself never runs into this, because its server tooling deploys Eclipse's own output. These plugins do the same for a folder you deploy from any editor.

## How the plugins pick the classes

The Gradle plugin adds `explodedWar` to `gradle build`. The editor compiles into its own folder, `bin/main`. If every `.java` file has a class there at least as new as itself, `WEB-INF/classes` gets the editor's classes. Otherwise it gets javac's, and the build names the file that's out of date. Resources, libraries and `src/main/webapp` always come from Gradle, so resource filtering and your `war` configuration still apply. If your editor compiles somewhere else, set `explodedHotswap { editorOutput = ... }`.

Maven has no such separate folder. The editor and Maven share `target/classes`, and `mvn compile`, `test` and `package` recompile the whole module with javac whenever a file was added or removed, after a clean, or after a pull. That's why the Maven goal compiles nothing. It runs your project's own `maven-war-plugin:exploded` on `target/classes` as the editor left them, with your war configuration, into `target/exploded/<finalName>.war`. It spots javac-built classes by their lambda method names, which is exactly the set HotSwap would reject, and warns about them.

## The Java version check

Both plugins fail the build when a class in the folder needs a newer Java than the project targets. I added this after Buildship caught me out. It reads the target level from the Gradle toolchain and ignores `options.release`, so Eclipse compiled Java 17 classes for a project that targets Java 11. WildFly on Java 11 then refused to load them, and the editor had happily let me call APIs Java 11 doesn't have.

## What about VS Code's javac mode?

The VS Code Java extension has an experimental setting, `java.jdt.ls.javac.enabled`, that makes the language server compile with javac instead of the Eclipse compiler. It really does write the `.class` files with javac, so the lambda naming mismatch goes away and a plain WAR can hot-swap. Mostly.

The catch is that you now have two javac versions. The setting needs the language server to run on JDK 24 or newer, while your build probably compiles on JDK 11 or 17. With `--release 11` both write Java 11 class files, but javac's code generation changes between versions. JDK 18, for example, stopped writing the outer-instance field into inner classes that never use it. A class hit by a change like that fails HotSwap the same way.

With these plugins that doesn't matter, because the folder holds exactly what the editor built, whichever compiler it used. Gradle needs no setting. For Maven, pass `-DexplodedHotswap.editorCompiler=javac`, or put `<editorCompiler>javac</editorCompiler>` in the plugin's configuration, so the goal stops warning about javac classes.

Other costs of the javac mode today:

- The language server itself has to run on JDK 24 or newer, set through `java.jdt.ls.java.home`.
- It only takes effect when projects are imported again.
- Its own project notes that javac-based operations can still be noticeably slower, particularly for big projects, and that the memory cost of keeping javac's state around is still being evaluated.

I'd try it on a small project. On a big one I'd wait.

## Limits

- HotSwap only replaces method bodies. Adding a method or field, or changing a signature, still needs a rebuild and redeploy. JetBrains Runtime and TravaJDK with HotswapAgent lift that limit, but your server then runs on a different JDK.
- HotSwap changes the running JVM, not the folder. Restart the server without rebuilding and you're back on the last build's classes.
- IntelliJ builds Gradle projects with Gradle by default, so it compiles and hot-swaps with the same javac and doesn't need this.
- Tested on Gradle 8.14 and 9.8, Maven 3.9, Java 11 and newer, and end to end against WildFly 29 through the debugger.

## License

Apache License 2.0
