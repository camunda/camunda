plugins { id("buildlogic.server-conventions") }

dependencies {
  api(libs.org.agrona.agrona)
  api(project(":zeebe-msgpack-core"))
  api(project(":zeebe-msgpack-codegen"))
  api(project(":zeebe-protocol"))
  implementation(libs.org.jspecify.jspecify)
  annotationProcessor(project(":zeebe-msgpack-codegen"))
}

description = "Zeebe Protocol Impl POJO: plain data classes with generated msgpack serializers"
