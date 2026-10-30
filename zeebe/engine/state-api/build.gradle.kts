import buildlogic.projectArtifact
import buildlogic.ArtifactKind

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  api(project(":zeebe-workflow-engine-model"))
  implementation(project(":zeebe-util"))
  api(project(":zeebe-stream-platform"))
  implementation(libs.io.camunda.security.library.api)
  api(libs.io.camunda.security.library.core)
  implementation(project(":camunda-security-protocol"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  api(project(":zeebe-db"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-msgpack-core"))
  implementation(project(":zeebe-dmn"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(project(":zeebe-bpmn-model"))
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.io.micrometer.micrometer.commons)
  implementation(libs.org.msgpack.msgpack.core)
  implementation(project(":zeebe-workflow-engine-common"))
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
  testImplementation(projectArtifact(":zeebe-workflow-engine-model", ArtifactKind.TESTS))
}

description = "Zeebe Workflow Engine State API: immutable and mutable state interfaces and persisted value types"
