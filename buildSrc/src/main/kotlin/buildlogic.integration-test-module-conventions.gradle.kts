import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

plugins {
    id("buildlogic.java-conventions")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies{
    add("implementation", platform(versionCatalog.findLibrary("org-apache-logging-log4j-log4j-bom").get()))
    add("annotationProcessor", platform(versionCatalog.findLibrary("org-apache-logging-log4j-log4j-bom").get()))
    add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-core").get())
    add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-slf4j2-impl").get())
}

val integrationTestIncludes = listOf(
    "**/IT*.class",
    "**/*IT.class",
    "**/*ITCase.class",
    "**/Test*.class",
    "**/*Test.class",
    "**/*Tests.class",
    "**/*TestCase.class",
)

val test by tasks.named<Test>("test") {
    description = "Runs Maven-style integration tests via the it task"
    dependsOn("it")
    exclude("**/*")
}

val ut by tasks.register<Test>("ut") {
    group = "verification"
    description = "No-op unit test task for integration-test-only modules"
    testClassesDirs = test.testClassesDirs
    classpath = test.classpath
    shouldRunAfter(test)
    exclude("**/*")
}

val it by tasks.register<Test>("it") {
    group = "verification"
    description = "Runs integration tests"
    testClassesDirs = test.testClassesDirs
    classpath = test.classpath
    shouldRunAfter(ut)
    include(integrationTestIncludes)
}

tasks.named("check") {
    dependsOn(it)
}
