import buildlogic.projectArtifact
import buildlogic.ArtifactKind
plugins { id("buildlogic.server-conventions") }

dependencies {
  testImplementation(project(":camunda-qa-util"))
  testImplementation(libs.org.springframework.spring.test)
  testImplementation(libs.org.springframework.spring.webflux)
  testImplementation(project(":camunda-zeebe"))
  testImplementation(projectArtifact(":camunda-qa-acceptance-tests", ArtifactKind.TESTS))
  testImplementation(project(":camunda-client-java"))
  testImplementation(project(":camunda-service"))
  testImplementation(project(":camunda-search-domain"))
  testImplementation(project(":camunda-gateway-mcp"))
  testImplementation(project(":camunda-exporter"))
  testImplementation(project(":camunda-db-rdbms"))
  testImplementation(project(":webapps-schema"))
  testImplementation(project(":zeebe-gateway-rest"))
  testImplementation(projectArtifact(":zeebe-gateway-rest", ArtifactKind.TESTS))
  testImplementation(project(":zeebe-protocol"))
  testImplementation(project(":zeebe-protocol-impl"))
  testImplementation(project(":camunda-security-protocol"))
  testImplementation(project(":zeebe-stream-platform"))
  testImplementation(project(":zeebe-workflow-engine"))
  testImplementation(project(":zeebe-util"))
  testImplementation(project(":zeebe-bpmn-model"))
  testImplementation(project(":zeebe-scheduler"))
  testImplementation(libs.com.tngtech.archunit.archunit)
  testImplementation(libs.com.tngtech.archunit.archunit.junit5.api)
  testImplementation(libs.com.tngtech.archunit.archunit.junit5.engine)
  testImplementation(libs.org.camunda.bpm.model.camunda.xml.model)
  testImplementation(libs.org.jspecify.jspecify)
  testImplementation(libs.org.ow2.asm.asm)
  testImplementation(libs.org.springframework.boot.spring.boot.autoconfigure)
  testImplementation(libs.org.springframework.spring.beans)
  testImplementation(libs.org.springframework.spring.context)
  testImplementation(libs.org.springframework.spring.web)
  testImplementation(libs.org.opensearch.client.opensearch.java)
  testImplementation(libs.com.fasterxml.jackson.core.jackson.databind)
  testImplementation(libs.jakarta.validation.jakarta.validation.api)
  testImplementation(libs.org.springframework.ai.spring.ai.mcp.annotations)
  testImplementation(libs.org.immutables.value)
}

description = "Camunda ArchUnit Tests"

configurations.named("testRuntimeClasspath") {
  exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
}

tasks.withType<Test>().configureEach { maxHeapSize = "8g" }
