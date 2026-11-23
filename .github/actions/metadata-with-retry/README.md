# Docker Metadata With Retry

## Intro

Runs [`docker/metadata-action`](https://github.com/docker/metadata-action), retrying up to three
times, so a transient GitHub API error does not fail an image build before any image work has
started.

`docker/metadata-action` reads the repository through the GitHub REST API
(`GET /repos/{owner}/{repo}`) on every run, and that call occasionally answers 5xx ("Server Error").
GitHub Actions has no retry primitive for a `uses:` step, so each attempt here is a literal copy of
the step, gated on the previous attempt's `outcome` (the same approach as
[`vault-action-with-retry`](../vault-action-with-retry)). See [Notes](#notes) for what that means when
you edit this action.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is referenced
  by local path.

## Usage

### Inputs

| Input  |                      Description                       | Required | Default |
|--------|--------------------------------------------------------|----------|---------|
| images | Image base name(s), e.g. `camunda/camunda`             | true     |         |
| tags   | Tags to generate, newline- or comma-separated          | true     |         |

### Outputs

| Output |                                  Description                                   |
|--------|--------------------------------------------------------------------------------|
| tags   | Fully qualified image tags, newline-separated, from the successful attempt     |

Only `tags` is forwarded, because it is the only output any caller reads. To forward another
`docker/metadata-action` output, add it to `outputs:` in `action.yml` with the same three-way chain.

## Notes

- **Three attempts, 10s apart, hard-coded.** A composite action has no loop, so the attempt count
  cannot be a parameter. Change it by adding or removing an attempt block.
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `docker/metadata-action` SHA.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed.
- **Outputs are gated on `outcome == 'success'`**, so a failed attempt never supplies a partial value.
- **A failed attempt still emits its error annotations.** `continue-on-error` absorbs the *step*, but
  the annotation is already published, so a green job can carry red annotations from this action.
  Each retry also emits a `::warning::` naming the attempt that failed.
- No failure classification: a genuine configuration error (e.g. an invalid `images` value) costs all
  three attempts before the job reports red.

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/metadata-with-retry
    id: meta
    with:
      images: camunda/camunda
      tags: ${{ inputs.version }}

  - name: Use the generated tags
    run: echo "${{ steps.meta.outputs.tags }}"
```
