import buildlogic.filterMavenResources
import buildlogic.zeebeUtilResourceTokens

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

tasks.named<ProcessResources>("processResources") {
  val tokens = project.zeebeUtilResourceTokens()
  this.filterMavenResources(this, "zeebeUtilResourceTokens", tokens)
}

dependencies {
  implementation(project(":camunda-cluster"))
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-protocol"))
  api(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.apache.commons.commons.lang3)
  api(libs.io.github.resilience4j.resilience4j.retry)
  api(libs.io.github.resilience4j.resilience4j.core)
  api(libs.com.google.guava.guava)
  testImplementation(libs.junit.junit)
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.net.bytebuddy.byte.buddy)
  compileOnly(libs.junit.junit)
}

description = "Zeebe Util"
