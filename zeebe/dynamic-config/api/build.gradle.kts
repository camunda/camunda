plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  api(project(":camunda-cluster"))
  api(project(":zeebe-atomix-messaging-api"))
  api(project(":zeebe-util"))
  api(project(":zeebe-scheduler"))
  implementation(libs.com.google.guava.guava)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.tools.jackson.core.jackson.core)
  implementation(libs.tools.jackson.core.jackson.databind)
  implementation(libs.com.fasterxml.jackson.core.jackson.annotations)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
}

description = "Zeebe Dynamic Cluster Configuration API"
