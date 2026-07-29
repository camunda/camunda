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

The nightly posts this Slack alert (to `@zeebe-medic`) when its `Destroy` step
fails, leaving orphaned AWS resources that keep billing until removed.

### 1. Identify the run

The alert names the prefix `aurora-it-<run_id>-<db_engine>` and links the run.
From the prefix you have both the `run_id` and the `db_engine`.

### 2. Run the cleanup

You need AWS credentials for the IT account (the same ones the workflow gets
from Vault) and `terraform` + `aws` + `jq` on PATH. From this directory:

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

`cleanup.sh` has two teardown paths and picks automatically:

- **Terraform path** — if the run's remote state still exists, it re-inits
  against that state key and runs `terraform destroy`, which removes resources
  in the required order.
- **`aws` fallback** — if the state is gone (or `terraform destroy` leaves
  resources behind), it deletes by discovered identifier in the same order
  (bastion EC2 → replica/primary instances → clusters detached from global →
  global cluster → subnet groups → security groups → IAM). This is safe to run
  unattended only because `<prefix>` contains the `run_id`, so the filters
  cannot match another run's resources.
  The aws fallback is more prone to drift from the terraform script, so it must
  be used with close attention. Prepare to see some resources not being destroyed.
  In that case the script must be updated to account for them. If the script fails
  you should still try to delete everything manually from aws console, using the
  arn provided by the script (with `--list`) to find all resources.

It then deletes the S3 state object and re-lists tags to confirm nothing
remains (non-zero exit if anything is left).

When run in a terminal it prompts before deleting; set `AUTO_APPROVE=true` to
skip the prompt (CI does this).

### 3. Verify

`./cleanup.sh --list <run_id> <db_engine>` returns empty tables for both
regions, and no `aurora-it-<run_id>-<db_engine>` objects remain in the
`aurora-it-tf-state-<account_id>` S3 bucket.
