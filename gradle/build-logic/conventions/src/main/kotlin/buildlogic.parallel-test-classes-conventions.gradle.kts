// Runs the test classes of a module in parallel; methods inside a class stay sequential.
//
// The settings are system properties of this module's own test task, so unlike a
// junit-platform.properties on the test classpath they cannot reach modules that depend on this one
// through its test jar. Apply the plugin in modules whose tests are safe to run concurrently; a
// module that cannot support this simply does not apply it.
tasks.withType<Test>().matching { it.name == "test" }.configureEach {
  systemProperty("junit.jupiter.execution.parallel.enabled", "true")
  systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
  systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
}
