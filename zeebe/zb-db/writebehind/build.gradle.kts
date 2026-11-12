plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  api(libs.org.agrona.agrona)
}

description = "Zeebe DB Write-Behind"
