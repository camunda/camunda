# Vault Action With Retry

## Intro

Runs [`hashicorp/vault-action`](https://github.com/hashicorp/vault-action), retrying up to three
times, so a transient network failure while logging into Vault or reading secrets does not fail the
whole job.

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

| Output  |                                         Description                                         |
|---------|---------------------------------------------------------------------------------------------|
| secrets | JSON map of all secrets read, keyed by the output names in `secrets`; parse with `fromJSON` |

Secrets are **not** exported as environment variables (`exportEnv: false`). An exported secret is
visible to every later step in the job, including third-party actions, whereas an output reaches
only the steps that explicitly reference it.

## Notes

- **Three attempts, 10s apart, hard-coded.** Retry-by-repetition cannot be parameterised: a
  composite action has no loop, so `max_attempts` would have to change the number of steps in the
  file. Change the count by adding or removing an attempt block.
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `hashicorp/vault-action` SHA. A mismatch means one attempt silently reads different
  secrets from the others.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **A failed attempt still emits its error annotations.** `continue-on-error` absorbs the *step*, but
  the annotation is already published to the check run and annotations have no notion of being
  absorbed — so a green job can carry red annotations from this action.
- **Guard `fromJSON` when this action can be skipped.** A skipped step has an empty `secrets`
  output and `fromJSON('')` fails the expression. Use `fromJSON(steps.<id>.outputs.secrets || '{}')`
  in steps that run regardless.
- No failure classification: a genuine configuration error (bad credentials, a wrong Vault URL, an
  invalid `secrets` mapping) costs all three attempts before the job reports red.

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/vault-action-with-retry
    id: secrets
    with:
      url: ${{ inputs.vault-address }}
      method: approle
      roleId: ${{ inputs.vault-role-id }}
      secretId: ${{ inputs.vault-secret-id }}
      secrets: |
        secret/data/github.com/organizations/camunda NEXUS_PSW | ci-account-password;
        secret/data/github.com/organizations/camunda NEXUS_USR | ci-account-username

  - name: Use imported secret
    env:
      CI_ACCOUNT_USERNAME: ${{ fromJSON(steps.secrets.outputs.secrets).ci-account-username }}
    run: echo "Configured user is ${CI_ACCOUNT_USERNAME}"
```

