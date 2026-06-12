import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named

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

// IT-only modules have no unit tests; ut is a no-op.
tasks.named<Test>("ut") {
    testClassesDirs = files()
    classpath = files()
}

// IT-only modules: run all test classes, no naming-convention filter needed.
tasks.named<Test>("it") {
    setIncludes(emptySet())
}

tasks.named("check") {
    dependsOn(tasks.named("it"))
}
