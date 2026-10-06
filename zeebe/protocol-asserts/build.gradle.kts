import buildlogic.GenerateAssertjAssertionsTask
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("buildlogic.server-conventions")
}

val assertjGeneratedDir = layout.buildDirectory.dir("generated-sources/assertj-assertions")
val patchedAssertjGeneratedDir =
    layout.buildDirectory.dir("generated-sources/assertj-assertions-patched")
val generatedAssertjClassesDir = layout.buildDirectory.dir("generated-classes/assertj")

val assertjGeneratorClasspath =
  configurations.create("assertjGeneratorClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }
val protocolClassDirectories =
  configurations.create("protocolClassDirectories") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
  }
val protocolCompileClasspath = configurations.compileClasspath

val generateAssertjAssertions =
  tasks.register<GenerateAssertjAssertionsTask>("generateAssertjAssertions") {
    group = "code generation"
    description = "Generate AssertJ assertions for Zeebe protocol record types"

    classDirs.from(protocolClassDirectories)
    classpath.from(protocolCompileClasspath)
    generatorClasspath.from(assertjGeneratorClasspath)
    outputDir.set(assertjGeneratedDir)
}

// Copy the generated assertions into a separate directory, patching the invalid Record<T>
// generics on the way. The generator therefore keeps exclusive ownership of its own output, which
// keeps it cacheable and up-to-date. `Sync` empties the destination before copying.
val patchRecordAssert = tasks.register<Sync>("patchRecordAssert") {
    group = "code generation"
    description = "Copy generated AssertJ assertions, patching RecordAssert generic types"

    from(generateAssertjAssertions.flatMap { it.outputDir })
    into(patchedAssertjGeneratedDir)
    filesMatching("**/RecordAssert.java") {
        filter { line: String ->
            line
                .replace("T value", "RecordValue value")
                .replace("T actualValue", "RecordValue actualValue")
        }
    }
}

dependencies {
    add(
        protocolClassDirectories.name,
        project(":zeebe-protocol", configuration = "mainClasses"),
    )
    add(
        protocolClassDirectories.name,
        project(":camunda-security-protocol", configuration = "mainClasses"),
    )
    add(assertjGeneratorClasspath.name, libs.org.assertj.assertj.assertions.generator)
    implementation(project(":zeebe-protocol"))
    implementation(project(":camunda-security-protocol"))
    implementation(libs.org.assertj.assertj.core)
    implementation(libs.javax.annotation.javax.annotation.api)
}

val compileGeneratedAssertjJava =
  tasks.register<JavaCompile>("compileGeneratedAssertjJava") {
    source(patchRecordAssert)
    classpath = files(sourceSets["main"].compileClasspath, protocolClassDirectories)
    destinationDirectory.set(generatedAssertjClassesDir)
    options.encoding = "utf-8"
}

sourceSets.named("main") {
    output.dir(compileGeneratedAssertjJava.flatMap { it.destinationDirectory })
}

tasks.named("classes") {
    dependsOn(compileGeneratedAssertjJava)
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val generatedAssertions = configurations.create("generatedAssertions") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("generatedAssertions", compileGeneratedAssertjJava.flatMap { it.destinationDirectory })
}

description = "Zeebe Protocol AssertJ Assertions"
