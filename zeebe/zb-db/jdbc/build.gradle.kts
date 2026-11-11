plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  api(project(":zeebe-protocol"))
  testImplementation(libs.com.h2database.h2)
}

description = "Zeebe DB JDBC"
