import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.JavaCompile

// For modules using spring-boot-configuration-processor together with a hand-written
// META-INF/additional-spring-configuration-metadata.json.
//
// The processor merges the additional metadata from the processed resources, which Maven always
// provides because process-resources runs before compile. Gradle only orders the two tasks if
// compileJava declares processResources as an input; otherwise the generated metadata silently
// loses the properties, defaults and group descriptions that only live in the additional file,
// depending on task scheduling (and the incomplete result can be replayed from the build cache).
plugins.withType<JavaPlugin> {
  tasks.named<JavaCompile>("compileJava") {
    inputs
      .files(tasks.named("processResources"))
      .withPathSensitivity(PathSensitivity.RELATIVE)
      .withPropertyName("additionalSpringConfigurationMetadata")
  }
}
