import buildlogic.projectArtifact
import buildlogic.ArtifactKind

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  api(project(":zeebe-workflow-engine-common"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-stream-platform"))
  implementation(libs.io.camunda.security.library.api)
  implementation(project(":camunda-security-protocol"))
  implementation(libs.io.camunda.security.library.validation)
  implementation(project(":camunda-secret-store-api"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-expression-language"))
  implementation(project(":zeebe-feel-tagged-parameters"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(project(":zeebe-bpmn-model"))
  implementation(project(":zeebe-auth"))
  implementation(libs.org.camunda.bpm.model.camunda.xml.model)
  implementation(libs.org.apache.commons.commons.lang3)
  implementation(libs.com.cronutils.cron.utils)
  implementation(libs.org.camunda.feel.feel.engine)
  implementation(libs.org.scala.lang.scala.library)
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.annotations)
  implementation(project(":camunda-search-domain"))
  testImplementation(project(":zeebe-scheduler"))
  testImplementation(project(":zeebe-logstreams"))
  testImplementation(projectArtifact(":zeebe-protocol-asserts", ArtifactKind.GENERATED_ASSERTIONS))
  testImplementation(projectArtifact(":zeebe-scheduler", ArtifactKind.TESTS))
  testImplementation(libs.junit.junit)
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.mockito.mockito.junit.jupiter)
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(projectArtifact(":zeebe-logstreams", ArtifactKind.TESTS))
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
}

description = "Zeebe Workflow Engine Model: BPMN/DMN model transformation and validation"
