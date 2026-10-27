# Use the Latest Minor Version of Gradle
`use_latest_minor_versions`

**Rule:** Stay on the latest minor version of the major Gradle release in use, and keep plugins on their latest compatible versions.

---

- **Fix:** `./gradlew wrapper --gradle-version <version>`, then update plugins and test compatibility. Upgrade Gradle before plugins.
