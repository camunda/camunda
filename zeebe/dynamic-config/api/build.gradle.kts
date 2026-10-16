plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":camunda-cluster"))
  api(project(":zeebe-atomix-cluster"))
  api(project(":zeebe-util"))
  api(project(":zeebe-scheduler"))
  implementation(libs.com.google.guava.guava)
  implementation(libs.org.jspecify.jspecify)
  api(libs.io.micrometer.micrometer.core)
  implementation(project(":zeebe-protocol"))
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.tools.jackson.core.jackson.core)
  implementation(libs.tools.jackson.core.jackson.databind)
  implementation(libs.com.fasterxml.jackson.core.jackson.annotations)
}

description = "Zeebe Dynamic Cluster Configuration API"
