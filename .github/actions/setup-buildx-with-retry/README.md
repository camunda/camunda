# Set Up Docker Buildx With Retry Action

## Intro

Boots a Buildx builder via
[`docker/setup-buildx-action`](https://github.com/docker/setup-buildx-action), retrying up to three
times. Booting the builder pulls `moby/buildkit` from Docker Hub, which answers 5xx often enough to
be the most frequent way an image build fails on healthy code.

Retries in a workflow are normally shell-command-only
([`nick-fields/retry`](https://github.com/nick-fields/retry), as used by
[`npm-ci-with-retry`](../npm-ci-with-retry)), and GitHub Actions has no retry primitive for a `uses:`
step. So each attempt here is a literal copy of the step, gated on the previous attempt's `outcome`.
See [Notes](#notes) for what that means when you edit this action.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is referenced
  by local path.
- If you pass an `endpoint`, that Docker context must already exist — this action does not create
  one. [`build-platform-docker`](../build-platform-docker) creates `zeebe-context` in the step before
  it calls this.

## Usage

### Inputs

|      Input      |                 Description                 | Required | Default |
|-----------------|---------------------------------------------|----------|---------|
| driver-opts     | Driver options passed to the builder        | false    | `""`    |
| buildkitd-flags | Flags passed to the buildkit daemon         | false    | `""`    |
| endpoint        | Docker context or endpoint to build against | false    | `""`    |

### Outputs

None. `setup-buildx-action`'s outputs (`name`, `driver`, `endpoint`, …) are not forwarded — no caller
needs them. The builder it creates becomes the active one for the job, which is how later
`docker/build-push-action` steps pick it up.

## Notes

- **Three attempts, 10s apart, hard-coded.** Retry-by-repetition cannot be parameterised: a
  composite action has no loop, so `max_attempts` would have to change the number of steps in the
  file. Change the count by adding or removing an attempt block.
- **The whole retry window is ~22 seconds, and almost all of it is sleep.** Measured in
  [run 34330823597](https://github.com/camunda/camunda/actions/runs/34330823597): an unreachable
  registry fails the boot in 0.7–0.9s, so three attempts plus two 10s gaps gave up 22.3s after the
  step started. This is the narrowest window of the three retry actions, because this is the
  fastest-failing wrapped action. It protects against a blip, **not an outage.** The throttling
  window behind INC-7723 lasted 30 minutes; neither this configuration nor the
  `Wandalen/wretry.action` one it replaced (identical `attempt_limit: 3` / `attempt_delay: 10000`)
  would have saved a single one of those jobs. What fixes that failure mode is that nothing is
  fetched at runtime any more — see below.
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `docker/setup-buildx-action` SHA. A mismatch means an attempt silently boots a different
  builder from its predecessor.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **A retry leaves one builder per attempt registered for the rest of the job, and all of them are
  torn down.** Each attempt calls `docker buildx create` with its own generated name, and each
  registers its own `post:` hook — including attempts that failed. Observed in
  [run 34330823597](https://github.com/camunda/camunda/actions/runs/34330823597), where three
  attempts produced three builders and post-job cleanup removed all three:

  ```
  docker buildx rm builder-c05196a0-…  removed   (attempt 3)
  docker buildx rm builder-8c43ea87-…  removed   (attempt 2)
  docker buildx rm builder-cb0753dc-…  removed   (attempt 1)
  ```

  So nothing leaks, but `docker buildx ls` is not a reliable way to identify *which* builder a
  later step used after a retry — the last successful attempt's builder is the active one, and the
  dead ones from earlier attempts are still listed alongside it.

- **A failed attempt still emits its error annotations.** `continue-on-error` absorbs the *step*, but
  the annotation is already published to the check run and annotations have no notion of being
  absorbed — so a green job can carry red annotations from this action.
  [`post-ci-failure-reasons`](../post-ci-failure-reasons) filters on job conclusion before it reads
  annotations, so a job that recovered produces no PR comment. The residual effect: if the job later
  fails for an unrelated reason, that comment will list these already-retried errors alongside the
  real one.

- No failure classification: a genuine error (bad `driver-opts`, a missing `endpoint` context) costs
  all three attempts before the job reports red.

This replaced `Wandalen/wretry.action`, which fetched the action it wrapped with an anonymous runtime
`git clone` — outside its own retry loop, and the single point of failure behind INC-7723. Dropping
it also removed the `Clear buildx inputs leaked into the job environment` workaround: wretry passed
inputs by exporting `INPUT_*` into `GITHUB_ENV`, where `version`, `name` and `platforms` outlived the
step and shadowed later steps' own defaults. See
[#62493](https://github.com/camunda/camunda/pull/62493).

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/setup-buildx-with-retry
    with:
      # host network is needed to push to the local registry used by our tests
      driver-opts: network=host
      endpoint: zeebe-context
```

Most jobs should not call this directly — [`build-platform-docker`](../build-platform-docker) already
does, together with the image build itself.
