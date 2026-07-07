val springBoot3Version = "3.5.16"
val spring6Version = "6.2.19"

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
