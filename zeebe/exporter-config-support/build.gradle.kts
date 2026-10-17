plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-exporter-api"))
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.org.jspecify.jspecify)
  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
}

description = "Zeebe Exporter Config Support"
