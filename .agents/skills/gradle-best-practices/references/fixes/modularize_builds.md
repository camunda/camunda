# Modularize Your Builds
`modularize_builds`

**Rule:** Split the source into several projects so Gradle can avoid and parallelize work.

---

- **Fix:** Propose a decomposition (`app/`, `util/`, `util-guava/`, …), each with its own build script, wired by `implementation(project(":util-guava"))`, applying each plugin only where it belongs. Structural — describe the plan and get confirmation before moving files.
