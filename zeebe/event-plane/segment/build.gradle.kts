plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.com.github.luben.zstd.jni)
  implementation(libs.org.jspecify.jspecify)
  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
  testImplementation(project(":zeebe-journal"))
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(project(":zeebe-util"))
  testImplementation(libs.org.agrona.agrona)
  testImplementation(libs.io.micrometer.micrometer.core)
}

description = "Zeebe Event Plane Segment"
