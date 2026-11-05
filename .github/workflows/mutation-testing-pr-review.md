---
# description: Non-blocking PIT mutation review of pull requests that change Distributed Systems production code
# test location: zeebe (DS-owned modules)
# type: pull request agentic workflow
# owner: @camunda/zeebe-distributed-platform
emoji: 🧬
name: mutation-testing-pr-review
description: Runs targeted PIT mutation testing on the production lines a pull request changes and leaves a non-blocking review about surviving mutants
intent: Tell the author which behaviour their change adds or alters that no test pins down, using mutants on the changed lines only, without blocking the merge
on:
  pull_request:
    # No ready_for_review: draft PRs are reviewed too, and marking one ready changes no code.
    types: [opened, synchronize, reopened]
    # PIT is only configured on main: stable branches have no pitest task, and backports were
    # already reviewed on main.
    branches: [main]
    # Distributed Systems-owned production code; mirrors CODEOWNERS. GitHub globs need no
    # trailing-slash gymnastics with "**src/main/**" (it also matches nested modules).
    paths:
      - "zeebe/atomix/**src/main/**"
      - "zeebe/backup/**src/main/**"
      - "zeebe/backup-stores/**src/main/**"
      - "zeebe/broker-client/**src/main/**"
      - "zeebe/dynamic-config/**src/main/**"
      - "zeebe/dynamic-node-id-provider/**src/main/**"
      - "zeebe/journal/**src/main/**"
      - "zeebe/logstreams/**src/main/**"
      - "zeebe/restore/**src/main/**"
      - "zeebe/restore-standalone/**src/main/**"
      - "zeebe/scheduler/**src/main/**"
      - "zeebe/snapshot/**src/main/**"
      - "zeebe/stream-platform/**src/main/**"
      - "zeebe/transport/**src/main/**"
      - "zeebe/broker/src/main/java/io/camunda/zeebe/broker/clustering/**"
      - "zeebe/broker/src/main/java/io/camunda/zeebe/broker/logstreams/**"
      - "zeebe/broker/src/main/java/io/camunda/zeebe/broker/partitioning/**"
      - "zeebe/broker/src/main/java/io/camunda/zeebe/broker/raft/**"
      - "zeebe/broker/src/main/java/io/camunda/zeebe/broker/transport/**"
engine:
  id: copilot
  # Scoped PIT runs and a few inline comments do not need a frontier model, and Luna costs a
  # fraction of Sonnet per token.
  model: gpt-6.1-luna
timeout-minutes: 45
env:
  TEST_OWNER: '@camunda/zeebe-distributed-platform'
concurrency:
  group: mutation-testing-pr-review-${{ github.event.pull_request.number }}
  cancel-in-progress: true
permissions:
  contents: read
  issues: read
  pull-requests: read
  # Copilot inference through the Actions token, so no PAT or API key secret is needed.
  copilot-requests: write
# GitHub-hosted on purpose: on gcp-core-16-default the gh-aw CLI proxy cannot write its TLS
# certificates under /tmp/gh-aw (permission denied), so the sandbox does not start there. The
# agent only runs scoped PIT, which fits the 4 cores here.
runs-on: ubuntu-latest
network:
  allowed:
    - defaults
    - github
    - github-actions
    - java
    # The java ecosystem covers Maven Central, the Google mirror and plugins.gradle.org.
    - downloads.gradle.org
tools:
  github:
    mode: gh-proxy
    toolsets: [default]
  # The pull request is untrusted input, so no open allowlist.
  bash: [cat, ls, find, grep, head, tail, wc, sort, jq, "git:*", "python3:*", "./gradlew:*"]
jobs:
  agent:
    timeout-minutes: 60
steps:
  - name: Set up Java
    uses: ./.github/actions/setup-java-with-retry
    with:
      distribution: temurin
      java-version: "21"
  - name: Set up Gradle
    uses: gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c # v6.4.1
    with:
      # Only main pushes warm the shared cache; this workflow only reads it.
      cache-read-only: true
  # GitHub's own diff of the pull request (merge base to head), which review comments attach to.
  # A diff against the base branch tip would also show, reversed, whatever landed on main since
  # the branch was cut. GitHub serves no diff for very large pull requests; the prompt falls back
  # to the GitHub tool then. /tmp/gh-aw is mounted into the agent sandbox.
  - name: Save the pull request diff for the agent
    env:
      GH_TOKEN: ${{ github.token }}
      PR_NUMBER: ${{ github.event.pull_request.number }}
    run: |
      mkdir -p /tmp/gh-aw/pr-review
      gh pr diff "$PR_NUMBER" --repo "$GITHUB_REPOSITORY" > /tmp/gh-aw/pr-review/pr.diff || true
      wc -l /tmp/gh-aw/pr-review/pr.diff
  # The agent sandbox runs as a different user than these steps, so it cannot write the Gradle
  # home setup-gradle created or the checkout (Gradle failed to create its wrapper lock file).
  # Download the distribution and configure the build here, without a daemon the sandbox could not
  # reuse, then let any user write both directories. The runner is discarded after the job.
  - name: Prepare Gradle for the agent sandbox
    run: |
      ./gradlew --no-daemon --console=plain -Pskip.fe.build help
      chmod -R a+rwX "$HOME/.gradle" "$GITHUB_WORKSPACE"
safe-outputs:
  create-pull-request-review-comment:
    side: RIGHT
  submit-pull-request-review:
    max: 1
    allowed-events: [COMMENT, REQUEST_CHANGES]
    supersede-older-reviews: true
---

# Mutation testing review of a pull request

Read `.agents/skills/mutation-testing/SKILL.md` in full and follow it for running PIT and
interpreting results.

You review pull request #${{ github.event.pull_request.number }} in `${{ github.repository }}`,
which changes Distributed Systems production code (cluster membership, raft, journal, snapshots,
backups, scheduling, dynamic configuration), where an unnoticed behaviour change is costly.

## Rules for this workflow

- **Changed lines only.** Report only mutants on lines this pull request adds or changes.
  Survivors anywhere else are not your business here, however bad: never mention them. The
  checkout is the pull request head, and `/tmp/gh-aw/pr-review/pr.diff` holds the pull request's
  diff as GitHub shows it (merge base to head). Its `+` lines are the changed lines; their
  line numbers count up from `c` in each `@@ -a,b +c,d @@` hunk header, skipping `-` lines. If
  the file is empty (GitHub serves no diff for very large pull requests), use the pull request's
  files and their patches from the GitHub tool. Do not diff against the base branch yourself:
  changes merged there since the branch was cut would show up as reversed changes. Ignore tests,
  build files, generated code and files outside Distributed Systems ownership. A changed file
  whose project has no `pitest` task: say so in the review summary instead of guessing.
- **PIT budget of about 15 minutes of wall time in total, on a 4-core runner** (about half the
  speed of the 16-core timings in the skill; pass `-Ppit.threads=4`). The run has a 45 minute budget and is
  cancelled when the pull request is pushed again, so cheap and focused beats thorough. Discover
  the relevant tests and choose the scope yourself, as the skill describes.
- **Run Gradle as a plain command.** You are already in the repository root and Gradle is set up
  for you: call `./gradlew ...` directly. The shell allowlist rejects anything wrapped around it
  (`cd ... &&`, `timeout`, environment-variable prefixes such as `GRADLE_USER_HOME=...`) and any
  direct `java` call; keep a run small instead of wrapping it in `timeout`.
- **Inline comments and exactly one review.** Use the `create-pull-request-review-comment` safe
  output on the exact line of each finding, and `submit-pull-request-review` once, summarizing in
  two or three sentences what was run (projects, classes, mutant counts) and what the comments say.
  Each inline comment is one finding in the skill's report format; the run details go only into
  the review summary.
- **`COMMENT` or `REQUEST_CHANGES` is your call.** Request changes when the surviving mutants on
  the changed lines show behaviour that should be tested before this pull request merges;
  otherwise comment. Judge all findings that are still open, including earlier ones you do not
  repeat: a new review dismisses this workflow's earlier change requests.
- **Prefer `noop` over noise.** If no mutant qualifies after dropping equivalent and low-value ones,
  call `noop` with a one-line reason (for example "no surviving mutants on changed lines in
  <classes>") and write no review, not even an "all good" one. One exception: if this workflow
  has a review in state `CHANGES_REQUESTED` on the pull request and its findings are all resolved
  now, a `noop` would leave that review blocking the merge. Submit a short `COMMENT` review
  instead, saying the earlier findings are resolved, which dismisses it.
- **Read-only.** Do not push to or modify the pull request branch. Changes to test sources while
  experimenting (a draft test to check that a mutant can be killed) are thrown away; do not leave
  edits behind. Do not change production code.
- **No repeats.** Read this workflow's earlier inline comments on the pull request with the GitHub
  tool (author `github-actions[bot]`; a comment whose line is gone is outdated). Do not repeat a
  finding on an unchanged line. A finding on a line this push changed again may be raised again.
- Use no safe output other than the review comments, the review, and `noop`. Do not label, assign,
  or comment on the pull request in any other way.
