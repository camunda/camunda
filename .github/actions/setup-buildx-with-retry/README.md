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
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `docker/setup-buildx-action` SHA. A mismatch means an attempt silently boots a different
  builder from its predecessor.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **What a retried boot leaves behind is not fully characterised.** `setup-buildx-action` names its
  builder itself and registers a `post:` teardown; whether a partly-booted builder from a failed
  attempt is cleaned up, and whether the retry leaves more than one registered, has not been probed.
  It has not caused a problem, but do not assume the state is clean if you are debugging a builder
  that behaves oddly after a retry.
- **A failed attempt still emits its error annotations.** They stay in the job log even when a later
  attempt succeeds, so a green job can contain red annotations from this action.
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
