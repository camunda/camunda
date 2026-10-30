import buildlogic.projectArtifact
import buildlogic.ArtifactKind

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  api(project(":zeebe-workflow-engine-state-mutable-api"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-stream-platform"))
  implementation(libs.io.camunda.security.library.api)
  implementation(libs.io.camunda.security.library.core)
  implementation(project(":camunda-security-protocol"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-msgpack-core"))
  implementation(project(":zeebe-expression-language"))
  implementation(project(":zeebe-dmn"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(project(":zeebe-bpmn-model"))
  implementation(libs.org.apache.commons.commons.lang3)
  implementation(libs.com.google.guava.guava)
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(project(":camunda-search-domain"))
  implementation(project(":zeebe-workflow-engine-model"))
  implementation(project(":zeebe-workflow-engine-state-api"))
  implementation(project(":zeebe-workflow-engine-common"))
  testImplementation(projectArtifact(":zeebe-workflow-engine-model", ArtifactKind.TESTS))
  testImplementation(projectArtifact(":zeebe-workflow-engine-state-api", ArtifactKind.TESTS))
  testImplementation(project(":zeebe-logstreams"))
  testImplementation(projectArtifact(":zeebe-protocol-asserts", ArtifactKind.GENERATED_ASSERTIONS))
  testImplementation(libs.junit.junit)
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.junit.platform.junit.platform.commons)
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
}

description = "Zeebe Workflow Engine State: RocksDB-backed state implementations and migrations"
