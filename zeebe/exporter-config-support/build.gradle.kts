plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-exporter-api"))
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Exporter Config Support"
