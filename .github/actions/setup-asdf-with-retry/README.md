# Setup asdf With Retry Action

## Intro

Installs [asdf](https://asdf-vm.com/) and one or more asdf plugins, retrying up to three times. The
shell implementation lives in `install-asdf-tools.sh` so normal shell tooling can parse, highlight,
and lint it. [`nick-fields/retry`](https://github.com/nick-fields/retry), as used by
[`npm-ci-with-retry`](../npm-ci-with-retry), wraps that script in retry logic.

`max_attempts`/`retry_wait_seconds`/`timeout_minutes` are action inputs because the retried unit is
a shell script.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is
  referenced by local path.
- A `.tool-versions` file with the plugins' desired versions must exist in `working-directory`.
- The action downloads the Linux AMD64 asdf release and is intended for Linux runners.

## Usage

### Inputs

|       Input        |                            Description                             | Required | Default  |
|--------------------|--------------------------------------------------------------------|----------|----------|
| plugins            | Space or newline separated asdf plugin names from `.tool-versions` | true     |          |
| working-directory  | Directory containing `.tool-versions`                              | false    | `.`      |
| asdf_version       | asdf release version to install                                    | false    | `0.20.0` |
| max_attempts       | Maximum number of attempts before giving up                        | false    | `3`      |
| retry_wait_seconds | Seconds to wait between attempts                                   | false    | `60`     |
| timeout_minutes    | Timeout per attempt in minutes                                     | false    | `2`      |

### Outputs

None. `asdf install` puts the tool on `PATH` via `GITHUB_PATH`/`GITHUB_ENV`, available to later
steps in the same job.

## Notes

- **A failed attempt removes `~/.asdf` before retrying** (`on_retry_command`), so a half-extracted
  asdf install from a killed attempt cannot make the next attempt fail differently than a clean one.
- **Multiple plugins are supported.** Pass them as a space or newline separated `plugins` input.
- No failure classification: a genuine 404 for a bad `asdf_version` is retried just like a transient
  network failure, and costs all attempts before the job reports red.
- For reusable workflows that check out an arbitrary source ref before calling this action, check out
  the workflow tooling into a separate path and call the action from there.

## Example

```yaml
steps:
  - uses: actions/checkout@v7
  - uses: ./.github/actions/setup-asdf-with-retry
    with:
      plugins: helm
```

```yaml
steps:
  - uses: actions/checkout@v7
    with:
      ref: ${{ inputs.ref }}

  - uses: actions/checkout@v7
    with:
      ref: ${{ inputs.tooling-ref || 'main' }}
      sparse-checkout: .github/actions/setup-asdf-with-retry
      path: workflow-actions

  - uses: ./workflow-actions/.github/actions/setup-asdf-with-retry
    with:
      plugins: helm
```

Production workflows should use the repository's pinned `actions/checkout` SHA.
