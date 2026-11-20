plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  api(libs.org.agrona.agrona)
  api(project(":zeebe-msgpack-core"))
  api(project(":zeebe-msgpack-value"))
  api(project(":zeebe-util"))
  implementation(libs.org.jspecify.jspecify)

  // run the processor from this module's own main output on test sources
  testAnnotationProcessor(sourceSets.main.get().output)
  testAnnotationProcessor(sourceSets.main.get().runtimeClasspath)

  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
}

description = "Zeebe Msgpack Codegen: annotations, runtime helpers and processor generating serializers for POJOs"
