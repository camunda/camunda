# Security

## Validate the Gradle Distribution SHA-256 Checksum · `validate_gradle_checksum` · High
When: `gradle/wrapper/gradle-wrapper.properties` exists.
Detect (det): no `distributionSha256Sum` key in that file.
Fix: Add the published `distributionSha256Sum` for the exact distribution in `distributionUrl`.

## Validate the Gradle Wrapper on every Upgrade · `validate_wrapper_checksum` · Medium
When: a wrapper exists. This is a process practice — it describes what the project's workflow should do, so report it as a recommendation about CI or review rather than a code defect, unless one of the signals below is present.
Detect (det): `distributionUrl` pointing somewhere other than `https://services.gradle.org/`; or no wrapper-validation step in CI configuration, where that configuration is visible in the project.
Fix: Use `gradle/actions/setup-gradle` (v4+), or regenerate the wrapper from a trusted install and diff.

## Do not Run `./gradlew` on Untrusted Projects · `run_gradle_on_external_projects` · Recommendation
When: operator guidance, not a property of the project under audit. Do not report it as a finding against the project. Mention it only if the audit surfaced something suspicious — an `exec` / `ProcessBuilder` call in a build script, an obfuscated string, or a `distributionUrl` on a non-Gradle host — in which case report *that*, with this anchor as the reference.
Detect (heur): `exec(`, `ProcessBuilder`, `Runtime.getRuntime().exec`, base64-looking literals, or downloads from unexpected hosts in build scripts.
Fix: Report the specific construct and what it does. Do not remove it silently.

## Build Output Should Be Byte-for-Byte Reproducible · `builds_should_be_reproducible` · Medium
When: the build produces an archive (`jar`, `war`, `zip`, or any `AbstractArchiveTask`).
Detect (det): `isPreserveFileTimestamps = true` / `preserveFileTimestamps = true`; `isReproducibleFileOrder = false` / `reproducibleFileOrder = false`; or no `java { toolchain { languageVersion = ... } }` block, which leaves the build dependent on the local `JAVA_HOME`.
Fix: Remove overrides of the Gradle 9 defaults and pin the JDK with a toolchain.

## Build your Published Artifacts Securely · `build-published-artifacts-securely` · Medium
When: the build publishes artifacts (`maven-publish`, a `publishing { }` block, or `signing`). This is a process practice about how publishing runs, not a property of the build scripts — report it as a recommendation about CI, and note that `builds_should_be_reproducible` is its prerequisite.
Detect (heur): visible CI configuration that publishes from a reused workspace or long-lived runner, a publish job depending on incremental or cached output, or a remote build cache left enabled on the publishing path. Where no CI configuration is visible, say so rather than asserting a violation.
Fix: Publish from a fresh, isolated, ephemeral CI machine with caches and prior outputs disabled.
