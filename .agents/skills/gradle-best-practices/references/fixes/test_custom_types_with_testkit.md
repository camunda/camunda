# Test your custom Task and Plugins with TestKit
`test_custom_types_with_testkit`

**Rule:** Test custom tasks and plugins with Gradle TestKit, so they graduate from prototypes in a build script into reusable, verified components.

---

- **Fix:**
  1. Move the custom type out of the build script into `build-logic/` (or a standalone plugin project) — this overlaps with `favor_composite_builds` in `structuring-builds.md`; report them together rather than twice.
  2. Add a dedicated functional test suite:

     ```kotlin
     val functionalTest = testing.suites.register("functionalTest", JvmTestSuite::class) {
         useJUnitJupiter()
         dependencies { implementation(gradleTestKit()) }
     }
     ```

  3. Register it with the plugin: `testSourceSets(functionalTest.get().sources)` under `gradlePlugin`.
  4. Cover task registration and configuration, execution and output, determinism across repeated runs, and cacheability under `--build-cache`.
  5. Wire it into `check` so it actually runs: `tasks.named("check") { dependsOn(functionalTest) }` — a lifecycle dependency, which is the permitted use of `dependsOn`.
