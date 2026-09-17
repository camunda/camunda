import io.camunda.gradle.pom.PomResolver
import io.camunda.gradle.pom.resolvePomProperty

val projectPomVersions =
  PomResolver(providers.fileContents(layout.projectDirectory.file("pom.xml")).asText.get())
    .properties()
val springBoot3Version = resolvePomProperty("version.spring-boot", projectPomVersions)
val spring6Version = resolvePomProperty("version.spring", projectPomVersions)

extra["springBoot3Version"] = springBoot3Version

extra["spring6Version"] = spring6Version

configurations.all {
  exclude(group = "org.springframework.boot", module = "spring-boot-health")
  resolutionStrategy.eachDependency {
    when (requested.group) {
      "org.springframework.boot" -> {
        useVersion(springBoot3Version)
        because("Spring Boot 3.x compatibility module")
      }
      "org.springframework" -> {
        useVersion(spring6Version)
        because("Spring 6.x required for Spring Boot 3.x")
      }
    }
  }
}
