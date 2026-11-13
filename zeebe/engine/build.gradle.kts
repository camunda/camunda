import buildlogic.projectArtifact
import buildlogic.ArtifactKind
plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-micrometer"))
  implementation(project(":zeebe-stream-platform"))
  implementation(project(":camunda-security-core"))
  implementation(project(":camunda-security-protocol"))
  implementation(libs.io.camunda.security.library.validation)
  implementation(libs.io.camunda.security.library.api)
  implementation(libs.io.camunda.security.library.core)
  implementation(project(":camunda-secret-store-api"))
  api(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-db-api"))
  testImplementation(project(":zeebe-db"))
  testImplementation(project(":zeebe-db-writebehind"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-msgpack-core"))
  implementation(project(":zeebe-expression-language"))
  implementation(project(":zeebe-feel-tagged-parameters"))
  implementation(project(":zeebe-dmn"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(libs.org.msgpack.jackson.dataformat.msgpack)
  implementation(project(":zeebe-bpmn-model"))
  implementation(project(":zeebe-auth"))
  api(libs.org.camunda.bpm.model.camunda.xml.model)
  implementation(libs.org.apache.commons.commons.lang3)
  implementation(libs.com.cronutils.cron.utils)
  implementation(libs.com.github.ben.manes.caffeine.caffeine)
  implementation(libs.org.scala.lang.scala.library)
  api(libs.com.google.guava.guava)
  api(libs.org.camunda.feel.feel.engine)
  api(libs.org.springframework.security.spring.security.crypto)
  api(libs.io.micrometer.micrometer.core)
  api(libs.io.micrometer.micrometer.commons)
  api(libs.com.fasterxml.jackson.core.jackson.annotations)
  api(libs.com.fasterxml.jackson.core.jackson.core)
  api(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(project(":camunda-search-client"))
  implementation(project(":camunda-search-domain"))
  testImplementation(project(":zeebe-scheduler"))
  testImplementation(projectArtifact(":zeebe-scheduler", ArtifactKind.TESTS))
  testImplementation(project(":zeebe-logstreams"))
  testImplementation(projectArtifact(":zeebe-logstreams", ArtifactKind.TESTS))
  testImplementation(projectArtifact(":zeebe-protocol-asserts", ArtifactKind.GENERATED_ASSERTIONS))
  testImplementation(libs.junit.junit)
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.org.hamcrest.hamcrest)
  implementation(libs.org.msgpack.msgpack.core)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.mockito.mockito.junit.jupiter)
  testImplementation(libs.org.apache.logging.log4j.log4j.api)
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.org.junit.platform.junit.platform.commons)
  testImplementation(libs.org.openjdk.jmh.jmh.core)
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
  testAnnotationProcessor(libs.org.openjdk.jmh.jmh.generator.annprocess)
  testImplementation(libs.org.instancio.instancio.core)
  compileOnly(libs.net.jcip.jcip.annotations)
}

tasks.withType<Test>().configureEach { jvmArgs("-Xmx4g") }

// Single-threaded engine load without JMH, e.g.:
// ZEEBE_DB_WRITE_BEHIND_ENABLED=true ./gradlew :zeebe-workflow-engine:engineLoad -Pinstances=100000
// With write-behind on, the engine key index set is used unless -PkeyIndex=none.
tasks.register<JavaExec>("engineLoad") {
  group = "verification"
  description = "Runs EngineLoadMain against the test runtime classpath"
  val testSourceSet = sourceSets.test.get()
  classpath = testSourceSet.runtimeClasspath
  mainClass.set("io.camunda.zeebe.engine.perf.EngineLoadMain")
  jvmArgs("-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED")
  // -Pjfr=/tmp/engine.jfr records a Flight Recording of the whole run
  project.findProperty("jfr")?.let { jvmArgs("-XX:StartFlightRecording=filename=$it,settings=profile") }
  // read through providers so that the configuration cache sees a changed variable
  environment(providers.environmentVariablesPrefixedBy("ZEEBE_DB_").get())
  environment(providers.environmentVariablesPrefixedBy("LOAD_").get())
  project.findProperty("instances")?.let { environment("LOAD_INSTANCES", it) }
  project.findProperty("warmup")?.let { environment("LOAD_WARMUP", it) }
  project.findProperty("prefill")?.let { environment("LOAD_PREFILL", it) }
  project.findProperty("revisit")?.let { environment("LOAD_REVISIT", it) }
  // default (when unset), none, or NAME[:POLICY],... e.g. -PkeyIndex=VARIABLES:KEEP_HIGHEST,JOBS
  project.findProperty("keyIndex")?.let { environment("ZEEBE_DB_KEY_INDEX", it) }
  project.findProperty("keyIndexMaxKeys")?.let { environment("ZEEBE_DB_KEY_INDEX_MAX_KEYS", it) }
}

description = "Zeebe Workflow Engine"
