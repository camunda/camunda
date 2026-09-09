# Build Docker Image With Retry Action

## Intro

Builds a platform Docker image via
[`docker/build-push-action`](https://github.com/docker/build-push-action), retrying up to three
times. BuildKit resolves the Dockerfile frontend and the base images through Docker Hub, whose token
endpoint answers 5xx or drops the connection often enough to fail the build before a single layer is
built.

Retries in a workflow are normally shell-command-only
([`nick-fields/retry`](https://github.com/nick-fields/retry), as used by
[`npm-ci-with-retry`](../npm-ci-with-retry)), and GitHub Actions has no retry primitive for a `uses:`
step. So each attempt here is a literal copy of the step, gated on the previous attempt's `outcome`.
See [Notes](#notes) for what that means when you edit this action.

This action is **not general-purpose.** It exists so that the three repeated attempt blocks live here
rather than in [`build-platform-docker`](../build-platform-docker), its only caller. Values that
caller passes identically for every image — `context: .`, `provenance: false`, `push: false`,
`target: app` — are fixed in the action rather than exposed as inputs. Adding a second caller means
promoting whichever of those it needs.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is referenced
  by local path.
- A Buildx builder must already be active — see [`setup-buildx-with-retry`](../setup-buildx-with-retry).
- When `push` is `true`, the job must already be logged into the target registry.

## Usage

### Inputs

|   Input    |                                              Description                                               | Required | Default |
|------------|--------------------------------------------------------------------------------------------------------|----------|---------|
| dockerfile | Path to the Dockerfile to build                                                                        | true     |         |
| tags       | Image tags to apply, newline- or comma-separated                                                       | true     |         |
| platforms  | Comma-separated list of target platforms                                                               | true     |         |
| push       | Whether to push the built image; when `false` the image is loaded into the local Docker daemon instead | true     |         |
| build-args | Build arguments, one `NAME=value` per line                                                             | false    | `""`    |

### Outputs

None. `build-push-action`'s `imageid`/`digest`/`metadata` outputs are not forwarded — the caller
derives the image name from `docker/metadata-action` instead. Forwarding them would mean picking
which attempt's outputs to return, since only one attempt's step id is knowable at author time.

## Notes

- **The push goes through `outputs:`, not through `push:`.** `build-push-action`'s own `push:` input
  is pinned to `false` in all three attempts. A pushing build instead sets
  `outputs: type=image,oci-mediatypes=false,push=true` — the `oci-mediatypes=false` is what the
  registries in use here need — and a non-pushing build sets `load: true` to leave the image in the
  local Docker daemon. Both are derived from this action's single `push` input. This is carried over
  from `build-platform-docker` unchanged; if you are changing it, establish why it was written this
  way first.
- **Three attempts, 10s apart, hard-coded.** Retry-by-repetition cannot be parameterised: a
  composite action has no loop, so `max_attempts` would have to change the number of steps in the
  file. Change the count by adding or removing an attempt block.
- **Editing one attempt means editing all three.** The `env:` and `with:` blocks must stay identical,
  including the pinned `docker/build-push-action` SHA. A mismatch means an attempt silently builds
  something different from its predecessor — the worst possible failure mode here, because the
  divergent build is the one that gets pushed.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **A retry is cheap up to the point that failed, but not free.** Layers already built are cached in
  the builder, which persists across attempts, so a replay resumes near where it broke — except that
  a failing `RUN` re-executes on every attempt.
- **A failed attempt still emits its error annotations.** They stay in the job log even when a later
  attempt succeeds, so a green job can contain red annotations from this action.
- No failure classification: a broken Dockerfile or a 4xx from the registry is retried just like a
  5xx, and costs all three attempts before the job reports red.
- `tags` may be multi-line. It is passed through as a plain string and only interpreted by
  `build-push-action`, which splits on newlines and commas alike.

This replaced `Wandalen/wretry.action`, which fetched the action it wrapped with an anonymous runtime
`git clone` — outside its own retry loop, and the single point of failure behind INC-7723. Dropping
it also removed two workarounds from the caller: the `Clear build-push inputs leaked into the job
environment` step (wretry passed inputs by exporting `INPUT_*` into `GITHUB_ENV`, where they outlived
the step) and the tags-to-single-line reduction (wretry re-parsed its `with:` block as YAML, so a
multi-line value broke the nested scalar). See
[#62493](https://github.com/camunda/camunda/pull/62493).

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/setup-buildx-with-retry
  - uses: ./.github/actions/docker-build-with-retry
    with:
      dockerfile: camunda.Dockerfile
      tags: ${{ steps.get-image.outputs.tags }}
      platforms: linux/amd64
      push: "false"
      build-args: |
        DISTBALL=camunda-zeebe.tar.gz
        VERSION=8.9.0-SNAPSHOT
```

Most jobs should not call this directly — [`build-platform-docker`](../build-platform-docker) already
does, together with the builder setup and the image naming.
