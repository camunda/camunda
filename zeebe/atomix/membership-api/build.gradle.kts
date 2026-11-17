plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":camunda-cluster"))
  api(project(":zeebe-atomix-utils"))
  implementation(libs.com.google.guava.guava)
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Atomix Membership API"
