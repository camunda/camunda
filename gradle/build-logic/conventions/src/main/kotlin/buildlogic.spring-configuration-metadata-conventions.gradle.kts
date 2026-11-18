import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.JavaCompile

// Makes compileJava run after processResources so Spring's configuration processor sees
// META-INF/additional-spring-configuration-metadata.json. Maven does this through its lifecycle:
// https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html
plugins.withType<JavaPlugin> {
  tasks.named<JavaCompile>("compileJava") {
    inputs
      .files(tasks.named("processResources"))
      .withPathSensitivity(PathSensitivity.RELATIVE)
      .withPropertyName("additionalSpringConfigurationMetadata")
  }
}
