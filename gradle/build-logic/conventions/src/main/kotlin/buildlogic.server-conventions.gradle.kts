import org.gradle.api.tasks.testing.Test

plugins { id("buildlogic.parent-conventions") }

if (providers.gradleProperty("includePerformanceTests").isPresent) {
  tasks.withType<Test>().configureEach {
    // Match Maven's include-performance-tests profile, which excludes this benchmark from CI.
    exclude("**/LargeStateControllerPerformanceTest.class")
  }
}
