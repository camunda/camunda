plugins { id("buildlogic.server-conventions") }

dependencies {
  api(libs.org.agrona.agrona)
  api(project(":zeebe-msgpack-core"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Msgpack Codegen: annotations, runtime helpers and processor generating serializers for POJOs"
