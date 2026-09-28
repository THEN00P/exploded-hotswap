# exploded-hotswap

Gradle and Maven plugins that deploy your editor's own classes, so HotSwap stops failing in VS Code.

You open a Java web app in VS Code, start WildFly in debug mode, change one line, and HotSwap refuses:

    delete method not implemented

Sometimes it says `add method not implemented` instead. The line you changed doesn't matter. It fails for every class that contains a lambda.

## Why it fails

VS Code compiles your code with the Eclipse compiler. So do Neovim, Zed and every other editor built on the Eclipse Java language server. HotSwap sends those classes to the server, but the server runs the classes Gradle or Maven built with javac.

The two compilers name the methods behind lambdas differently. javac writes `lambda$render$0`, the Eclipse compiler writes `lambda$0`. The JVM can only swap method bodies, so it reads the new class as one method removed and another added, and rejects it.

Eclipse itself never runs into this, because its server tooling deploys Eclipse's own output. These plugins do the same for a folder you deploy from any editor.

## Gradle

```kotlin
plugins {
  war
  id("io.github.then00p.exploded-hotswap")
}
```

`gradle build` now also writes `build/exploded/<name>.war`. Deploy that folder instead of the WAR file. In VS Code that's Runtime Server Protocol, Add Deployment, then pick the folder. Start the server in debug mode and edit.

The editor compiles into its own folder, `bin/main`, which makes this easy. If every `.java` file has a class there at least as new as itself, the deployed folder gets the editor's classes. If not, it gets javac's, and the build names the file that's out of date:

    explodedWar: javac's classes, since com/example/Foo.java is newer than its class in .../bin/main.
    HotSwap from the editor fails for classes with lambdas until the editor has built the project.

Resources, libraries and `src/main/webapp` always come from Gradle, so resource filtering and your `war` configuration still apply.

If your editor compiles somewhere else, point the plugin at it:

```kotlin
explodedHotswap {
  editorOutput = layout.projectDirectory.dir("bin/main")
}
```

## Maven

```xml
<plugin>
  <groupId>io.github.then00p</groupId>
  <artifactId>exploded-hotswap-maven-plugin</artifactId>
  <version>0.1.0</version>
</plugin>
```

Maven is harder. The editor and Maven write to the same `target/classes`, and `mvn compile`, `mvn test` and `mvn package` recompile the whole module with javac whenever a file was added or removed, after a clean, or after a pull. HotSwap then fails at what look like random times.

So while you work, run `mvn exploded-hotswap:exploded` instead of `mvn package`, and deploy `target/<finalName>`. The goal compiles nothing. It runs your project's own `maven-war-plugin:exploded` on `target/classes` as the editor left them, with your war configuration. If javac compiled some of those classes, it warns:

    [WARNING] 3 class(es) in target/classes were compiled by javac, com/example/Foo.class among them.
    HotSwap from an editor on the Eclipse compiler (VS Code, Eclipse, Neovim, Zed) fails for them:
    mvn compile, test and package recompile the whole module with javac. Let the editor rebuild the
    project (VS Code: "Java: Rebuild Projects"), then run this again.

It spots javac by its lambda method names, which is also exactly the set of classes HotSwap would reject. After a stray `mvn package`, run "Java: Rebuild Projects" in VS Code and the warning goes away.

## The Java version check

Both plugins fail the build when a class in the folder needs a newer Java than the project targets. I added this after Buildship caught me out. It reads the target level from the Gradle toolchain and ignores `options.release`, so Eclipse compiled Java 17 classes for a project that targets Java 11. WildFly on Java 11 then refused to load them, and the editor had happily let me call APIs Java 11 doesn't have.

The fix is to give the editor the same level as the build. In Gradle that's `java { toolchain { ... } }` or `sourceCompatibility`. In Maven it's `maven.compiler.release`.

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
