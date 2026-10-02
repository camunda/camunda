# Vault Action With Retry

## Intro

Runs [`hashicorp/vault-action`](https://github.com/hashicorp/vault-action), retrying up to three
times. Logging into Vault and reading secrets goes over the network, and a transient failure there
used to fail the whole job (INC-8449).

Retries in a workflow are normally shell-command-only
([`nick-fields/retry`](https://github.com/nick-fields/retry), as used by
[`npm-ci-with-retry`](../npm-ci-with-retry)), and GitHub Actions has no retry primitive for a `uses:`
step. So each attempt here is a literal copy of the step, gated on the previous attempt's `outcome`.
See [Notes](#notes) for what that means when you edit this action.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is referenced
  by local path.
- The caller must supply valid Vault AppRole credentials and a `secrets` mapping accepted by
  `hashicorp/vault-action`.

## Usage

### Inputs

|  Input   |                        Description                         | Required | Default |
|----------|------------------------------------------------------------|----------|---------|
| url      | Vault URL                                                  | true     |         |
| method   | Authentication method; currently the callers use `approle` | true     |         |
| roleId   | Vault AppRole role ID                                      | true     |         |
| secretId | Vault AppRole secret ID                                    | true     |         |
| secrets  | Multi-line Vault secret mapping forwarded to the action    | true     |         |

### Outputs

This wrapper forwards the secret outputs currently used by [`setup-build`](../setup-build):

|       Output        |         Description         |
|---------------------|-----------------------------|
| ci-account-password | Imported Nexus password     |
| ci-account-username | Imported Nexus username     |
| dockerhub-token     | Imported DockerHub token    |
| dockerhub-username  | Imported DockerHub username |
| minimus-token       | Imported Minimus token      |
| harbor-username     | Imported Harbor username    |
| harbor-password     | Imported Harbor password    |

## Notes

- **Three attempts, 10s apart, hard-coded.** Retry-by-repetition cannot be parameterised: a
  composite action has no loop, so `max_attempts` would have to change the number of steps in the
  file. Change the count by adding or removing an attempt block.
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `hashicorp/vault-action` SHA. A mismatch means one attempt silently reads different
  secrets from the others.
- **Outputs are static.** Composite actions cannot dynamically forward an arbitrary set of Vault
  outputs derived from the `secrets` input. Every output this wrapper exposes must be declared in
  `action.yml`, and each one must resolve across attempt 1/2/3 explicitly.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **A failed attempt still emits its error annotations.** `continue-on-error` absorbs the *step*, but
  the annotation is already published to the check run and annotations have no notion of being
  absorbed — so a green job can carry red annotations from this action.
- No failure classification: a genuine configuration error (bad credentials, a wrong Vault URL, an
  invalid `secrets` mapping) costs all three attempts before the job reports red.

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - id: secrets
    uses: ./.github/actions/vault-action-with-retry
    with:
      url: ${{ inputs.vault-address }}
      method: approle
      roleId: ${{ inputs.vault-role-id }}
      secretId: ${{ inputs.vault-secret-id }}
      secrets: |
        secret/data/github.com/organizations/camunda NEXUS_PSW | ci-account-password;
        secret/data/github.com/organizations/camunda NEXUS_USR | ci-account-username
```

Most jobs should not call this directly — [`setup-build`](../setup-build) is the main caller, along
with the rest of the build bootstrap.
