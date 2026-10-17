# Validate the Gradle Wrapper on every Upgrade
`validate_wrapper_checksum`

**Rule:** Treat wrapper changes as security-sensitive: verify the wrapper JAR and distribution settings whenever Gradle is upgraded.

---

- **Fix:** Use `gradle/actions/setup-gradle` (v4+), which validates the wrapper JAR, or regenerate the wrapper with a trusted Gradle install and diff the result against what is committed.
