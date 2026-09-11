import io.camunda.gradle.pom.PomResolver
import io.camunda.gradle.pom.resolvePomProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

// Scoped version overrides for Optimize modules — mirrors Maven parent POM dependency management
// scoped to optimize/* only, preventing leakage into non-optimize modules.
val optimizePom = PomResolver(rootDir.resolve("optimize/pom.xml").readText()).properties()

fun optVersion(key: String) = resolvePomProperty(key, optimizePom)

configurations.all {
  resolutionStrategy.force(
    // Versions from optimize/pom.xml <properties>
    "com.github.sisyphsu:dateparser:${optVersion("version.dateparser")}",
    "com.icegreen:greenmail:${optVersion("greenmail.version")}",
    "com.opencsv:opencsv:${optVersion("version.opencsv")}",
    "com.sun.mail:jakarta.mail:${optVersion("version.jakarta-mail")}",
    "com.tdunning:t-digest:${optVersion("version.t-digest")}",
    "com.vdurmont:semver4j:${optVersion("version.semver4j")}",
    "io.github.netmikey.logunit:logunit-log4j2:${optVersion("logunit.version")}",
    "io.github.netmikey.logunit:logunit-core:${optVersion("logunit.version")}",
    "org.apache.lucene:lucene-core:${optVersion("version.lucene")}",
    "org.eclipse.angus:jakarta.mail:${optVersion("version.jakarta-mail-angus")}",
    "org.elasticsearch:elasticsearch:${optVersion("version.elasticsearch")}",
    "org.glassfish.jersey.core:jersey-client:${optVersion("version.jersey")}",
    "org.glassfish.jersey.media:jersey-media-json-jackson:${optVersion("version.jersey")}",
    "org.mock-server:mockserver-client-java:${optVersion("mockserver.version")}",
    "org.mock-server:mockserver-core:${optVersion("mockserver.version")}",
    "org.mock-server:mockserver-netty:${optVersion("mockserver.version")}",
    "org.mock-server:mockserver-junit-jupiter:${optVersion("mockserver.version")}",
    "org.mockito:mockito-inline:${optVersion("mockito-inline.version")}",
    "org.quartz-scheduler:quartz:${optVersion("quartz.version")}",
  )
}

// Dedicated per-suite unit-test tasks: testCoreFeatures and testDataLayer.
//
// Optimize splits its backend unit tests into two owner-aligned JUnit Platform @Suite classes —
// OptimizeCoreFeaturesTestSuite (@camunda/core-features) and OptimizeDataLayerTestSuite
// (@camunda/data-layer) — and CI runs each suite as its own job so failures route to the right
// owner.
//
// We deliberately do NOT select a suite via `test --tests "*OptimizeCoreFeaturesTestSuite"`.
// Gradle's `--tests` is a post-discovery name filter: when it matches one suite, the
// junit-platform-suite engine still discovers the OTHER suite and applies the same filter to its
// children, stripping them all. The emptied sibling then throws NoTestsDiscoveredException and
// fails the build. (Maven never hits this: `-Dtest=<Suite>` selects only that suite class, and
// surefire runs with failIfNoSpecifiedTests=false.)
//
// `include("**/<Suite>.class")` is the faithful Gradle equivalent of Maven's `-Dtest=<Suite>`:
// only the named suite class is handed to the launcher as a discovery selector, so the sibling
// suite is never discovered and cannot be stranded. The suite's own @SelectPackages then expands
// normally. Both tasks are registered for every Optimize module; CI only invokes them on modules
// that actually contain the matching suite (see ci-optimize.yml module lists), so a module lacking
// a suite simply never runs the corresponding task.
plugins.withId("java") {
  val testSourceSet = extensions.getByType<SourceSetContainer>().named("test")
  tasks.register<Test>("testCoreFeatures") {
    group = "verification"
    description = "Runs the Optimize Core Features unit-test suite only."
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    include("**/OptimizeCoreFeaturesTestSuite.class")
  }
  tasks.register<Test>("testDataLayer") {
    group = "verification"
    description = "Runs the Optimize Data Layer unit-test suite only."
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    include("**/OptimizeDataLayerTestSuite.class")
  }
}
