plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-state-mutable-api"))
  api(libs.io.micrometer.micrometer.core)
  api(libs.io.micrometer.micrometer.commons)
  api(libs.com.google.guava.guava)
  implementation(project(":zeebe-expression-language"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.apache.commons.commons.lang3)
}

description = "Zeebe Workflow Engine State: RocksDB-backed state implementations and migrations"
