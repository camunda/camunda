# Performance

## Use UTF-8 File Encoding · `use_utf8_encoding` · Medium
When: always.
Detect (det): `org.gradle.jvmargs` in the root `gradle.properties` does not contain `-Dfile.encoding=UTF-8`, including the case where `org.gradle.jvmargs` is absent entirely.
Fix: Add `-Dfile.encoding=UTF-8` to `org.gradle.jvmargs` in the root `gradle.properties`.

## Use the Build Cache · `use_build_cache` · Medium
When: always.
Detect (det): root `gradle.properties` lacks `org.gradle.caching=true`, or sets it to `false`.
Fix: Add `org.gradle.caching=true` to the root `gradle.properties`.

## Use the Configuration Cache · `use_configuration_cache` · High
When: always.
Detect (det): root `gradle.properties` lacks `org.gradle.configuration-cache=true`, or sets it to `false`.
Fix: Add `org.gradle.configuration-cache=true`, then run the build and fix what it surfaces.

## Avoid Expensive Computations in Configuration Phase · `avoid_computations_in_configuration_phase` · High
When: always.
Detect (heur): work at the top level of a build script, or inside a `tasks.register` / `tasks.create` configuration block, rather than in a `@TaskAction` / `doLast`. Markers: `File(...).readText()`, `readLines()`, `Files.` calls, `URL(...)`, `exec` / `providers.exec` results consumed eagerly, `Thread.sleep`, or a loop over files outside a task action.
Fix: Move the logic into a `@TaskAction` or a lazily-queried `Provider`, and declare its inputs.

## Prefer the `-bin` Gradle Distribution · `prefer_bin_distribution` · Recommendation
When: a wrapper exists.
Detect (det): `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` ends with `-all.zip`.
Fix: Change the `distributionUrl` suffix to `-bin.zip`; re-fetch `distributionSha256Sum` if set.
