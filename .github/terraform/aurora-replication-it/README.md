# Aurora Async Replication IT — infrastructure & recovery

Terraform + tooling for the **Aurora Async Replication IT** nightly job
(`.github/workflows/aurora-async-replication-test.yml`). The workflow provisions
a two-region Aurora Global Database (primary `eu-west-1`, secondary `eu-west-2`)
plus an SSM bastion, runs the replication acceptance tests against it, then
tears everything down.

## Files

|     File     |                               Purpose                               |
|--------------|---------------------------------------------------------------------|
| `main.tf`    | The Aurora global cluster, networking, IAM and SSM bastion.         |
| `cleanup.sh` | Recovery tool to tear down infrastructure a failed run left behind. |

## Resource naming

Every resource is named and tagged from a per-run, **per-engine** prefix:

```
aurora-it-<run_id>-<db_engine>          e.g. aurora-it-30418390623-postgresql
```

and tagged `Name=<prefix>` / `Purpose=aurora-async-replication-it`. The
Terraform state key is derived from the same prefix, so the two engine jobs
(`mysql`, `postgresql`) that run under one GitHub Actions run never share names
or state. **The `db_engine` component is load-bearing** — dropping it makes the
two parallel jobs collide (`EntityAlreadyExists`, duplicate security groups,
shared/corrupt state). See `Generate Terraform variables` in the workflow.

## Runbook: "Aurora infrastructure cleanup failed — manual action required"

The workflow makes one automatic cleanup attempt in its `Destroy` step. When
that succeeds, the workflow then attempts to remove the Terraform state and no
manual resource cleanup is needed. If `Destroy` fails (or the run was started
with `skip_destroy`), the workflow
does **not** invoke `cleanup.sh` automatically: it leaves the resources and
state in place and posts this Slack alert (to `@zeebe-medic`). The alert means
that manual cleanup is required; the resources may continue to incur AWS
charges until they are removed.

### 1. Identify the run

The alert names the prefix `aurora-it-<run_id>-<db_engine>` and links the run.
From the prefix you have both the `run_id` and the `db_engine`.

### 2. Run the cleanup

You need AWS credentials for the IT account (the same ones the workflow gets
from Vault) and `terraform` + `aws` + `jq` on PATH. Before running the script,
authenticate through Okta to the **Core Foundation Playground** using the
AWS IAM Identity Center profile for the IT account/role. If the profile is not
configured yet, create it once and then sign in:

```bash
aws configure sso --profile <profile>
aws sso login --profile <profile>
export AWS_PROFILE=<profile>
```

If the profile is already configured, only `aws sso login --profile <profile>`
and the `AWS_PROFILE` export are needed. Verify that the selected identity is
the intended account before deleting anything:

```bash
aws sts get-caller-identity
```

From this directory:

```bash
# See what is still alive first (read-only, both regions):
./cleanup.sh --list <run_id> <db_engine>

# Tear it down (terraform destroy in the correct dependency order, then purge state):
./cleanup.sh <run_id> <db_engine>
```

To target a name that does not follow the `aurora-it-<run_id>-<db_engine>`
convention (e.g. an old-scheme `aurora-it-<run_id>` from before the per-engine
split), pass the full prefix explicitly:

```bash
./cleanup.sh --list --prefix aurora-it-30418390623
./cleanup.sh --prefix aurora-it-30418390623
```

`cleanup.sh` is a manually invoked recovery tool. After you confirm the
prefix, it selects between two teardown paths automatically:

- **Terraform path** — if the run's remote state still exists, it re-inits
  against that state key and runs `terraform destroy`, which removes resources
  in the required order.
- **`aws` fallback** — if the state is gone (or `terraform destroy` leaves
  resources behind), it deletes by discovered identifier in the same order
  (bastion EC2 → replica/primary instances → clusters detached from global →
  global cluster → subnet groups → security groups → IAM). This is safe to run
  unattended only because `<prefix>` contains the `run_id`, so the filters
  cannot match another run's resources.

The fallback is best effort and more prone to drift from the Terraform
configuration. Watch its output: the command exits non-zero if tagged resources
remain. If anything is left, use the ARNs from `--list` to remove the remaining
resources manually in the AWS console (or update the script before retrying).

It then deletes the S3 state object and re-lists tags to confirm nothing
remains (non-zero exit if anything is left).

When run in a terminal it prompts before deleting; set `AUTO_APPROVE=true` to
skip the prompt (CI does this).

### 3. Verify

`./cleanup.sh --list <run_id> <db_engine>` returns empty tables for both
regions, and no `aurora-it-<run_id>-<db_engine>` objects remain in the
`aurora-it-tf-state-<account_id>` S3 bucket.
