# Prefer the `-bin` Gradle Distribution
`prefer_bin_distribution`

**Rule:** Prefer the smaller `-bin` distribution over `-all`, which additionally carries sources and documentation.

---

- **Fix:** Change the URL suffix to `-bin.zip`. If `distributionSha256Sum` is also set, it must be re-fetched for the `-bin` artifact — the checksums differ (see `security.md`).
- **Don't:**

  ```properties
  distributionUrl=https\://services.gradle.org/distributions/gradle-<version>-all.zip
  ```

- **Do:**

  ```properties
  distributionUrl=https\://services.gradle.org/distributions/gradle-<version>-bin.zip
  ```
