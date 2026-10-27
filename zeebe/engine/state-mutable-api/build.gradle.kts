plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-state-api"))
  implementation(project(":zeebe-stream-platform"))
  implementation(project(":camunda-security-protocol"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-workflow-engine-model"))
  implementation(project(":zeebe-workflow-engine-common"))
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
}

description = "Zeebe Workflow Engine Mutable State API: the write interfaces that only appliers and migrations use"
