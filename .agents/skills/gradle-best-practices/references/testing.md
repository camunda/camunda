# Testing

Read this file only if the project defines a custom task type or plugin. A build that merely consumes plugins has nothing to check here — count the entry as not applicable.

## Test your custom Task and Plugins with TestKit · `test_custom_types_with_testkit` · Medium
When: the project defines a custom task type or plugin, in `buildSrc/`, `build-logic/`, or inline in a build script.
Detect (det): a custom task class (`: DefaultTask()`, `extends DefaultTask`) or plugin (`: Plugin<Project>`, `implements Plugin<Project>`) exists, while no `gradleTestKit()` dependency and no `GradleRunner` usage appear anywhere in the build.
Fix: Move the type into `build-logic/`, then add a TestKit functional test suite. Structural.
