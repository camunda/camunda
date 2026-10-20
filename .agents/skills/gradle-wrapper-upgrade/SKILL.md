---
name: gradle-wrapper-upgrade
description: "Use whenever the user asks to upgrade, bump, or update the Gradle wrapper, `gradlew`, `distributionUrl`, `gradle-wrapper.properties`, or the project's Gradle version, on Gradle 7.0–9.x — or asks *how* that is done. Covers both modes: 'upgrade the wrapper' means perform it; a 'how do I upgrade' question is answered with the copy-pasteable commands rather than by running them. Not for adding a wrapper to a project that has none."
license: Apache-2.0
metadata:
  author: gradle
  version: "1.0.0"
---

# Gradle Wrapper Upgrade

## Two modes — match the user's verb

Decide this first; everything below is conditional on it.

- **"Upgrade the wrapper", "bump Gradle to 8.14.4"** → act: work the steps, verify, report.
- **"How do I upgrade the wrapper?"** → give the commands, name the run-it-twice gotcha, stop. A "how" question is answered, not executed — including when you have already opened with "I'll upgrade the wrapper." Say the commands; do not run them.

If genuinely ambiguous, give the commands.

The steps below are the same material either way: in act mode you run them, in answer mode you hand them over.

Upgrading means running the built-in `wrapper` task, never hand-editing files: `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` are generated, and the next `wrapper` run reverts any edit without warning. **Scope:** a wrapper that already exists — adding one to a project with none is a different job (`gradle :wrapper` from a system Gradle install).

Two references ship beside this file, for branches most upgrades never hit: **`references/snapshot.md`** (Step 3 — securing the wrapper where git cannot) and **`references/rollback.md`** (Step 6 — undoing an upgrade the build cannot take). **Open either only when the step says to.**

## Step 1 — Orient

Confirm `gradlew` is in the project root and read `distributionUrl` from `gradle/wrapper/gradle-wrapper.properties`. It carries both things you need: the **current version** (`gradle-8.14-bin.zip` → 8.14) and the **distribution type** (`-bin` or `-all`).

Commands below are `./gradlew` (Unix/macOS/WSL/Git Bash); Windows cmd → `gradlew.bat`, PowerShell → `.\gradlew`. Options are identical.

## Step 2 — Pin the target version and its checksum

Use the version the user named. **If they named none, upgrade to the latest release**, resolved to a concrete version:

```
curl -s https://services.gradle.org/versions/current     # → version + checksumUrl
```

Resolve before you pin — no checksum can be looked up for `--gradle-version=latest`. Pinning the sum is standard on every upgrade, not an add-on; it is what verifies the download.

**The sum is the distribution URL with `.sha256` appended**, and that one line is the whole lookup:

```
curl -sL https://services.gradle.org/distributions/gradle-9.0.0-bin.zip.sha256
```

`.sha256`, **not `.sha256sum`** — that one 404s on every version and reads like the release doesn't exist. `-L` is required: the URL `301`s to `downloads.gradle.org`, and `-f` won't catch that because it only fails on 4xx and 5xx, so HTML back means you didn't follow the redirect. The body is the bare hex sum. A genuine 404 → the `checksumUrl` in the JSON above, or https://gradle.org/release-checksums/ — not another guessed URL.

**Match the sum to the zip named in `distributionUrl`.** Each release publishes separate sums for the `-bin` zip, the `-all` zip, and the wrapper JAR. The wrong one fails with `Verification of Gradle distribution failed!`, which reads like a tampered download rather than the copy-paste error it usually is.

Once `distributionSha256Sum` is set, any later `wrapper` run that *changes* `--gradle-version` must pass a new matching sum — the build fails rather than reuse the stale one. If the version doesn't change, the sum is preserved automatically.

## Step 3 — Baseline the build, then secure the wrapper

First, establish that the build works *before* you touch it:

```
./gradlew --console=plain tasks
```

Keep the exit code. This is the canary, and Steps 5 and 6 re-run it: **a red canary after an upgrade means nothing if it was red beforehand.**

**`tasks`, not `build`.** `tasks` applies every plugin and registers every task — the exact surface a new major breaks — while compiling and running nothing, so it is fast and fails only for reasons the upgrade caused. `build` goes red on a flaky test or an unreachable repository, and rolling back on that would revert upgrades that were fine.

If the build sets `org.gradle.configuration-on-demand=true`, use `tasks --all` instead — plain `tasks` configures the root project and little else, so it would pass while a subproject is broken. **Whichever form you pick, use that identical command every later time**; `tasks` before against `tasks --all` after is not a comparison.

**If the canary fails here, stop and do not upgrade.** `:wrapper` configures the build too, so Step 4 will fail for the same reason — there is no supported way to upgrade a build that will not configure. Report what broke; getting it green on its current version comes first.

Then make sure you can get the four wrapper files back:

```
git status --short gradlew gradlew.bat gradle/wrapper
```

**Silent output is all you need.** Git is the snapshot: `git checkout -- gradlew gradlew.bat gradle/wrapper` restores all four at any point, so there is nothing to copy and nothing to clean up afterwards.

**Anything else — output, or not a git repository — read `references/snapshot.md` and take a copy before going on.** Do not skip it. Without a snapshot there is no rollback, and Step 6 assumes one exists.

## Step 4 — Run the `wrapper` task twice

```
./gradlew --console=plain :wrapper --gradle-version=8.14.4 --gradle-distribution-sha256-sum=<sha256>
./gradlew --console=plain :wrapper --gradle-version=8.14.4 --gradle-distribution-sha256-sum=<sha256>
```

Same command, twice: the first regenerates the scripts and jar from the *old* Gradle's templates, the second from the new version's. Both are required — see below. The leading `:` targets the root project, the only one the task belongs to.

**An upgrade never changes the distribution type.** `-all.zip` in Step 1 → add `--distribution-type=all` to both runs; the task defaults to `bin` and will otherwise switch the project silently. Changing type is the user's decision, not a side effect of an upgrade.

Running as an agent:

- **Trust the exit code; never grep for `BUILD SUCCESSFUL`.** A failed checksum must not read as a successful upgrade. A nonzero exit from either run sends you to Step 6, not to a third attempt.
- **The second run downloads the full distribution (100 MB+).** A long silence is normal, not a hang — abandoning it leaves the half-done state described below, and is the one rollback with no error message to quote.
- **`--console=plain`** keeps the output parseable: no progress bar, no colors.

## Step 5 — Verify the files, then run the canary

```
git status --short gradle/wrapper gradlew gradlew.bat
```

On a real version change, all four files show as modified: `gradle-wrapper.properties`, `gradle-wrapper.jar`, `gradlew`, `gradlew.bat`. **Only the properties moved → the second run didn't happen.** Working from a snapshot instead of git? Compare against it as `references/snapshot.md` describes. Then `./gradlew --version` should report the target version. That check can't stand alone either way: the version it prints comes from `distributionUrl`, so it looks correct even while the scripts and jar are stale.

Those check that the upgrade *landed*. They say nothing about whether the build still works, so re-run the canary — **the same command as Step 3**:

```
./gradlew --console=plain tasks
```

**Nonzero exit → Step 6.** Green here, against the green baseline from Step 3, is the upgrade verified: delete the snapshot and report.

## Step 6 — If the build no longer configures, roll back and stop

Two ways to get here, and the second is the one that matters:

- **The `wrapper` task itself failed.** Run 2 *is* the new version, so a build it cannot configure takes the second invocation down with it. Obvious, and it leaves the properties pointing at a version the project cannot run.
- **The `wrapper` task succeeded and the canary failed.** The upgrade landed cleanly, all four files are correct, and the build is now unusable. This is the dangerous one, because every file-level check in Step 5 passes and the agent that stops there reports a successful upgrade over a broken project.

Either way the cause is the same: a new major removes APIs, raises the minimum JVM, and ships a new Groovy, and a build script, a plugin, or a `buildSrc` that relies on what went away no longer configures. That is a blocked upgrade, not a partial one.

**Read `references/rollback.md` now and follow it** — restoring the four files without scattering them, verifying the restore, and the four things the closing report must say. Do not improvise it from this page.

Nothing else here applies once you are on this branch. Do not retry the upgrade, and do not edit build scripts to force it through.

## Why run the task TWICE — the #1 real-world gotcha

The task always writes all four files, but the scripts and jar come from the **currently running** Gradle's templates, not the target version's. Run 1 (still the old Gradle) points the properties at the new version and rewrites the generated files from the *old* templates — usually reproducing what was already there, which is also where a hand-edited `gradlew` quietly disappears. Run 2 auto-downloads the new version, because the properties changed, and rewrites them from the *new* templates.

Symptoms of a half-done upgrade, very common in support questions: wrapper or deprecation warnings that won't go away, an odd-looking `gradlew` diff, or a bot (e.g. Dependabot) bumping `distributionUrl` without ever running the task. The fix is always the same — run it twice.

## Version formats

`--gradle-version` also accepts `latest`, `release-candidate`, `release-nightly`, `nightly`, and `release-milestone`, plus a bare `9` or `9.1` on **Gradle 9+**; prefer a concrete version whenever you are pinning a checksum. Since **Gradle 9.0**, `gradle-wrapper.properties` must state a full `X.Y.Z` — a bare major or major.minor is rejected *in the file*, though the CLI still accepts it. Confirm your version's options with `./gradlew help --task=:wrapper`.

## Out of scope

**Fixing the build is out of scope in both directions** — neither to force a blocked upgrade through, nor to repair what the new version broke. A mechanically correct upgrade that leaves the build unusable is still a failed job, and the answer is Step 6, not a patch.

**The dividing line is the build phase, not the command.** No longer *configures* — the `wrapper` task dies, or the canary does — the upgrade is blocked: roll back. Configures and then fails during *execution* — a compile error, a failing test — the upgrade landed and the build has a separate problem: leave the new wrapper in place and report it. Rolling back on a red test is as wrong as leaving behind a build that cannot configure. The line sits there because configuration failure is unambiguous and cheap to check and execution failure is neither; `tasks` passing does not promise the build compiles.

Canonical: https://docs.gradle.org/current/userguide/gradle_wrapper.html.
