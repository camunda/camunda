plugins { id("buildlogic.server-conventions") }

dependencies {
  api(libs.org.agrona.agrona)
  api(project(":zeebe-msgpack-core"))
  api(project(":zeebe-msgpack-value"))
  api(project(":zeebe-util"))
  implementation(libs.org.jspecify.jspecify)

  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
}

description = "Zeebe Msgpack Codegen: annotations, runtime helpers and processor generating serializers for POJOs"
