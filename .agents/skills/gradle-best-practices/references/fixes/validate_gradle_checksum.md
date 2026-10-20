# Validate the Gradle Distribution SHA-256 Checksum
`validate_gradle_checksum`

**Rule:** Set `distributionSha256Sum` in `gradle-wrapper.properties` so the downloaded distribution's integrity is verified.

---

- **Fix:** Add the published checksum for the exact distribution named in `distributionUrl`:

  ```properties
  distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
  distributionSha256Sum=<sha256 from gradle.org/release-checksums>
  ```

  The checksum is specific to the `-bin`/`-all` variant and the version — do not carry one over when either changes. If the correct checksum cannot be obtained during this session, report the finding and the exact value needed rather than inventing one.
