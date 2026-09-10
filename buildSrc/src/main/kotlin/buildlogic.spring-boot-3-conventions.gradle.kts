import buildlogic.parsePomProperties
import buildlogic.pomVersion

val projectPomVersions =
  parsePomProperties(providers.fileContents(layout.projectDirectory.file("pom.xml")).asText.get())
val springBoot3Version = pomVersion(projectPomVersions, "version.spring-boot")
val spring6Version = pomVersion(projectPomVersions, "version.spring")

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
