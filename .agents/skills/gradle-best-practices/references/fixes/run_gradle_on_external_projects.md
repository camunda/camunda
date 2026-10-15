# Do not Run `./gradlew` on Untrusted Projects
`run_gradle_on_external_projects`

**Rule:** Running `./gradlew` executes arbitrary build logic; inspect an unfamiliar project before running or opening it in an IDE.

---

- **Fix:** Report the specific construct and what it does. Do not remove it silently.
