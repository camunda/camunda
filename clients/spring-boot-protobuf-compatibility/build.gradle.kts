plugins { id("buildlogic.server-conventions") }

val springBootBaselineBom =
  configurations.create("springBootBaselineBom") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
  }

dependencies {
  testImplementation(project(":camunda-client-java"))
  testImplementation(project(":zeebe-gateway-protocol-impl"))
  testImplementation(libs.com.google.protobuf.protobuf.java.spring.boot.baseline)
  add(
    springBootBaselineBom.name,
    "org.springframework.boot:spring-boot-dependencies:${libs.versions.parent.spring.boot.oldest.managing.protobuf.get()}@pom",
  )
}

// parent-conventions pins every org.springframework.boot artifact to the current Spring Boot
// version, which would swap the baseline BOM for the current one. Re-pin it to the baseline.
springBootBaselineBom.resolutionStrategy.eachDependency {
  if (requested.group == "org.springframework.boot") {
    useVersion(libs.versions.parent.spring.boot.oldest.managing.protobuf.get())
  }
}

configurations
  .matching { it.name == "testCompileClasspath" || it.name == "testRuntimeClasspath" }
  .configureEach {
    resolutionStrategy.force(
      "com.google.protobuf:protobuf-java:${libs.versions.protobuf.spring.boot.baseline.get()}"
    )
  }

tasks.named<ProcessResources>("processTestResources") {
  from(springBootBaselineBom) { rename { "spring-boot-dependencies.pom" } }
}

description = "Camunda Spring Boot Protobuf Compatibility"
