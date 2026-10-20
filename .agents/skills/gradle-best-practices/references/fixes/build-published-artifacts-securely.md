# Build your Published Artifacts Securely
`build-published-artifacts-securely`

**Rule:** Publish from a secure, isolated build so the artifacts are verifiably the product of the source. Byte-for-byte reproducibility (`builds_should_be_reproducible`) is the prerequisite: without it the other safeguards assure nothing, because there is no way to confirm what the artifact was built from.

---

- **Fix:** Publish from a fresh, isolated, ephemeral CI machine — one that starts from a clean slate with no lingering artifacts, caches, or external state. Disable the build cache and any incremental reuse on the publishing path, and do not publish from a developer workstation or a reused CI workspace.

This practice has no `Don't`/`Do` code pair in the documentation, because the change is to the publishing pipeline rather than to the build scripts. What to report instead:

- **Incremental or local builds on the publish path** — they consume outputs from earlier builds, so a compromised earlier build propagates into the release.
- **A remote build cache left enabled while publishing** — it can serve stale or malicious entries, and makes the artifact's integrity depend on machine state rather than on source.
- **A reused workspace or long-lived runner** — the same objection, one level up.

Report these as recommendations about the project's CI configuration, naming the job or workflow file. If the project ships no visible CI configuration, say that the practice could not be evaluated rather than asserting a violation.
