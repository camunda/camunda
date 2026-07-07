import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named

plugins { id("buildlogic.java-conventions") }

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  add(
    "implementation",
    platform(versionCatalog.findLibrary("org-apache-logging-log4j-log4j-bom").get()),
  )
  add(
    "annotationProcessor",
    platform(versionCatalog.findLibrary("org-apache-logging-log4j-log4j-bom").get()),
  )
  add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-core").get())
  add(
    "testRuntimeOnly",
    versionCatalog.findLibrary("org-apache-logging-log4j-log4j-slf4j2-impl").get(),
  )
}

// IT-only modules have no unit tests; test is a no-op.
tasks.named<Test>("test") {
  testClassesDirs = files()
  classpath = files()
}

// IT-only modules: run all test classes, no naming-convention filter needed.
tasks.named<Test>("it") {
  setIncludes(emptySet())

  // Mirror the Maven `smoke-test` profile: run only @Tag("smoke-test") tests, and honor
  // -PexcludedGroups (comma-separated tags) the same way Maven's -DexcludedGroups does
  // (used to skip the "container" tag on runners without Docker).
  val smokeTest = providers.gradleProperty("smoke-test").isPresent
  val excludedGroups =
    providers
      .gradleProperty("excludedGroups")
      .orNull
      ?.split(",")
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      .orEmpty()

  useJUnitPlatform {
    if (smokeTest) {
      includeTags("smoke-test")
    }
    if (excludedGroups.isNotEmpty()) {
      excludeTags(*excludedGroups.toTypedArray())
    }
  }
}

tasks.named("check") { dependsOn(tasks.named("it")) }
