plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-protocol"))
  api(project(":zeebe-util"))
  api(libs.io.micrometer.micrometer.core)
  testImplementation(libs.com.h2database.h2)
}

description = "Zeebe DB API"
