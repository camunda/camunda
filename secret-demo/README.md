# Centralized Secret Resolution demo

A fully local, reproducible walkthrough of centralized secret resolution
([epic #56555](https://github.com/camunda/camunda/issues/56555)) on Camunda 8 Run, backed by the
file-based secret store.

Everything here runs on your own machine.

What it shows:

- A file-based secret store, configured per cluster (and overridable per physical tenant).
- `camunda.secrets.<name>` references in a job worker task's input mappings.
- A job worker receiving already-resolved values, and nothing else in the system seeing them.
- A secret reference carried by a cluster variable, resolved the same way.
- `POST /v2/secrets/resolve` and `POST /v2/secrets/list`, guarded by the `SECRET` authorization
  resource.

Long polling only. Job push (job streaming) does not resolve secrets yet, so every activation in
this demo goes through `POST /v2/jobs/activation`.

## Prerequisites

- A clone of `camunda/camunda` on `main` (or a branch containing the feature).
- JDK 21+ and `JAVA_HOME` set.
- Go 1.25+ (to build the `c8run` CLI).
- Maven access to Camunda's artifact repositories (the standard monorepo build prerequisite).
- `curl` and `jq`.

## Run it

Two scripts, same eleven use cases, same narration. Pick by whether the terminal is on camera.

### Hands off: [run-demo.sh](run-demo.sh)

```bash
./run-demo.sh
```

Does everything itself: checks prerequisites, resets leftover state, starts the cluster, walks the
use cases one at a time, and tears the environment down again so the next run starts from exactly
the same state. Each use case explains what it is about, waits for you to press Enter, runs, and
then says what happened and where to look. Use this when the terminal is your prompter and the
camera is on Operate.

The build in the next section is a one-time prerequisite. Everything after it is handled by the
script.

Flags:

- `--keep-running` - leave the cluster up at the end instead of tearing it down.
- `--no-reset` - run the use cases against a cluster that is already up.
- `--yes` - run without prompts, for verifying the script itself.

Each use case's response is kept under `output/`, so you have the JSON to point at afterwards.

### Live: [present-demo.sh](present-demo.sh)

```bash
./present-demo.sh
```

Sets the environment up itself, exactly as `run-demo.sh` does, then hands you the demo one command
at a time: it prints the exact `curl` for each step, copies it to your clipboard, and waits while
you paste it into the shell that is on camera, so the audience sees each request and its response
live. During the use cases it changes nothing on its own; it only reads the cluster and prints
commands with runtime values such as a process instance key or an incident key already substituted
in, so there is nothing to copy by hand.

It cleans up after itself at the end, so it can be run again immediately.

Each step carries two lines of narration: **SAY BEFORE**, to read out with the command on screen,
and **SAY AFTER**, to read out over the response. Both are written to be spoken as they stand.

Flags:

- `--no-clipboard` - print only, do not touch the clipboard.
- `--skip-prep` - do not set anything up; the cluster is already running.
- `--no-teardown` - leave the cluster running instead of cleaning up at the end.
- `--self-test` - run every printed command instead of waiting, to verify the script still works.
  Not a way to present; use `run-demo.sh` for that.

## Build (one time)

Everything is built from your local checkout, so no release or nightly artifact is involved.

1. Build the distribution from the repository root (~10 min):

   ```bash
   ./mvnw -B -T1C -DskipTests -DskipChecks -Dflatten.skip=true -Dskip.fe.build=false -DskipOptimize package
   ```

   This produces `dist/target/camunda-zeebe-8.10.0-SNAPSHOT.tar.gz`. Keep `-Dskip.fe.build=false`:
   the webapp frontend is needed for the Operate screens in the walkthrough.

2. Stage the distribution into `c8run/` and build the CLI:

   ```bash
   cp dist/target/camunda-zeebe-8.10.0-SNAPSHOT.tar.gz c8run/
   cd c8run
   tar -xzf camunda-zeebe-8.10.0-SNAPSHOT.tar.gz
   go build -o c8run ./cmd/c8run
   ```
3. Point `c8run/.env` at that version (it is a local-only file, never committed):

   ```dotenv
   CAMUNDA_VERSION=8.10.0-SNAPSHOT
   ```
4. Create the secrets and start the cluster:

   ```bash
   export SECRET_DEMO_DIR="$(cd ../secret-demo && pwd)/secrets"
   ../secret-demo/scripts/01-create-secrets.sh
   ./c8run start --config ../secret-demo/demo-config.yaml --disable-connectors
   ```

   The store lives at `secret-demo/secrets/`, which is git-ignored so no secret file is ever
   committed. `SECRET_DEMO_DIR` is read by [demo-config.yaml](demo-config.yaml) as the store's
   directory; the scripts default to the same path, so exporting it is only needed because c8run
   itself resolves the placeholder. Connectors are disabled because this demo drives the job
   worker path by hand.

   The `--config` path has to be **relative to `c8run/`**. c8run joins it onto its own base
   directory, so an absolute path is concatenated onto that and fails to open.

Startup should log:

```text
io.camunda.application.commons.secrets.SecretStoreConfiguration - Registered file secret store 'default' for physical tenant 'default'
```

Operate is at <http://localhost:8080/operate>, credentials `demo` / `demo`.

Stop the cluster with `./c8run stop`.

## What the configuration does

[demo-config.yaml](demo-config.yaml) is loaded after c8run's own `configuration/application.yaml`,
so it overrides it. Three things matter:

- `camunda.secrets.stores.file.default.path` is the store. The id **must** be `default`: that is
  the store a `camunda.secrets.<name>` reference addresses, and any other id is rejected at
  startup.
- `camunda.secrets.cache.ttl` is lowered to the minimum of `1m` (default `20m`) so a rotated file
  is picked up during the demo.
- `camunda.security.authorizations.enabled` is turned **on** and `unprotected-api` **off**. C8Run
  ships with authorizations disabled, in which case every authenticated caller is treated as
  authorized for every reference, and the permission scenes would prove nothing.

The commented `camunda.physical-tenants.*` block shows the per-tenant override. It is not
exercised here: secret authorization under a physical tenant currently resolves grants against
root storage ([#58393](https://github.com/camunda/camunda/issues/58393)), so the permission scenes
cannot run in that mode yet.

## Walkthrough

Every script lives in [scripts/](scripts) and is numbered in the order it is used. They all default
to the same secret store directory (`secret-demo/secrets`), so nothing needs exporting unless you
moved it.

### 1. The secret store

```bash
./scripts/01-create-secrets.sh
```

One file per secret: the file name is the secret name, the file contents are the value. This is
exactly how Kubernetes projects a mounted Secret volume, so the same directory works unchanged
with k8s, External Secrets Operator, or the Secrets Store CSI driver. Files are read on every
call, so rotation needs no restart.

`tls.crt` is deliberately in there: the store accepts that file name, but it cannot form a valid
`camunda.secrets.<name>` reference, and step 8 shows the listing endpoint leaving it out.
`missingToken` is deliberately absent until step 6.

### 2. Reference secrets in the model, and deploy

[models/order-process.bpmn](models/order-process.bpmn) has one service task whose input mappings
reference two secrets:

```xml
<zeebe:input source="=&#34;Bearer &#34; + camunda.secrets.apiToken" target="authorization" />
<zeebe:input source="=camunda.secrets.dbPassword" target="dbPassword" />
```

```bash
./scripts/02-deploy.sh
```

### 3. A reference must be an expression

```bash
./scripts/03-deploy-bad-literal.sh
```

[models/bad-literal-process.bpmn](models/bad-literal-process.bpmn) uses the same reference as a
static value (`camunda.secrets.apiToken`, no leading `=`). Deployment is rejected with HTTP 400:

```text
ERROR: Secret reference(s) 'camunda.secrets.apiToken' must be used as an expression
(e.g. '=camunda.secrets.<name>'), not as a string literal, in input mapping source
'camunda.secrets.apiToken'.
```

A reference therefore cannot be smuggled in as arbitrary text, and a runtime value that merely
looks like a reference is never resolved.

### 4. The job worker receives resolved values

```bash
./scripts/04-start-instance.sh secret-demo-order
./scripts/05-activate-job.sh send-order
```

A short pause, then:

```json
"variables": {
  "authorization": "Bearer sk-live-DEMO-9f3ad41c",
  "dbPassword": "p4ssw0rd-from-the-store"
}
```

That pause is the async design. The job's secret was not cached, so the job was parked in a
dedicated waiting state and the resolution was requested in the background; command processing is
never blocked on store I/O. This single long-polling request stays open and picks the job up as
soon as it is reactivated, so the worker makes no second call.

Nothing notifies workers when a parked job becomes activatable again, so what picks it up is the
gateway's long-polling probe. [demo-config.yaml](demo-config.yaml) sets
`camunda.api.long-polling.probe-timeout` to `2s` (default `10s`) to keep that pause short. With the
default, a `requestTimeout` under 10s would return empty even though the secret resolved a second
in.

The worker never learns which store the value came from, and needs no secret configuration of its
own.

### 5. Nothing else sees the value

```bash
./scripts/06-show-stored-variables.sh <processInstanceKey>
```

The variables held in the cluster still read `"Bearer camunda.secrets.apiToken"` and
`"camunda.secrets.dbPassword"`. The resolved value is substituted into the activation response
only, so it is never written to state, exported, or shown in Operate. The script also greps
`c8run/log/` for both values and expects no match.

Open the same instance in Operate to see the placeholder on screen.

### 6. A missing secret raises an incident, and the incident is recoverable

```bash
./scripts/04-start-instance.sh secret-demo-missing
./scripts/05-activate-job.sh charge-card     # parks the job, requests resolution
./scripts/07-show-incidents.sh
```

[models/missing-secret-process.bpmn](models/missing-secret-process.bpmn) references
`camunda.secrets.missingToken`, which no file backs. Background resolution retries with backoff and
then raises a `SECRET_RESOLUTION_ERROR` incident, shown in Operate as "Secret resolution error":

```text
Failed to resolve secret 'missingToken' from the configured secret store. Ensure the secret exists
and the store is available, then resolve the incident to retry.
```

The job was parked the whole time, so no worker ever saw a half-resolved job. Now supply the secret
and retry:

```bash
./scripts/08-add-missing-secret.sh
./scripts/09-resolve-incident.sh <incidentKey>   # or press Retry on the incident in Operate
./scripts/05-activate-job.sh charge-card
```

Resolving the incident makes the parked job activatable again; the next activation resolves against
the store, which now holds the file, and delivers `paymentToken`. No redeploy, no restart.

### 7. Secret permissions and the resolve endpoint

`demo` is an admin, and the default admin role already carries `SECRET:READ` and `SECRET:REVEAL`
on `*`, so it needs no grant:

```bash
./scripts/12-resolve-secrets.sh demo camunda.secrets.apiToken
```

Create three users with no grants at all, and watch the default deny:

```bash
./scripts/10-create-users.sh
./scripts/12-resolve-secrets.sh noGrantUser camunda.secrets.apiToken
```

```json
{"resolved": [], "errors": [{"reference": "camunda.secrets.apiToken", "code": "ACCESS_DENIED"}]}
```

Grant one single reference, then send a batch of three:

```bash
./scripts/11-grant.sh revealUser REVEAL camunda.secrets.apiToken
./scripts/12-resolve-secrets.sh revealUser \
  camunda.secrets.apiToken camunda.secrets.dbPassword camunda.secrets.doesNotExist
```

One HTTP 200 carries all three outcomes independently: the granted reference in `resolved`, and
both others as `ACCESS_DENIED`. Note the third one: it does not exist in the store, yet the caller
is told `ACCESS_DENIED`, not `NOT_FOUND`. Authorization runs before any store lookup, so an
unauthorized caller never learns whether a secret exists.

`NOT_FOUND` is what an *authorized* caller gets for a reference nothing holds:

```bash
./scripts/12-resolve-secrets.sh demo camunda.secrets.apiToken camunda.secrets.doesNotExist
```

Two further properties the script surfaces:

- the response carries `Cache-Control: no-store`, so a browser or intermediary proxy cannot retain
  a secret-bearing response;
- `./scripts/12-resolve-secrets.sh --anonymous camunda.secrets.apiToken` is rejected with HTTP 401.

Grants are read through secondary storage, so allow a moment after granting before the new
permission takes effect.

### 8. Secret reference listing

```bash
./scripts/11-grant.sh listUser READ '*'
./scripts/13-list-secrets.sh listUser
```

Names only, never values. `tls.crt` is absent: it cannot form a valid reference, so listing it
would only suggest something every expression would reject. This is the endpoint Modeler
autocompletion uses.

The listing is also filtered per caller:

- `./scripts/13-list-secrets.sh noGrantUser` returns an empty list.
- `./scripts/13-list-secrets.sh revealUser` returns an empty list too: `REVEAL` does not imply
  `READ`.
- a user granted `READ` on one reference sees only that one, even though the store holds more.

### 9. A secret reference carried by a cluster variable

```bash
./scripts/14-create-cluster-variable.sh
./scripts/04-start-instance.sh secret-demo-cluster-var
./scripts/05-activate-job.sh notify-slack
```

The cluster variable `slackConfig` is created with `kind: SECRET_REFERENCE`, and its stored value
holds `"token": "camunda.secrets.apiToken"`.
[models/cluster-variable-process.bpmn](models/cluster-variable-process.bpmn) names no secret at
all; it reads `=camunda.vars.env.slackConfig.token`. The engine folds the reference the cluster
variable carries onto the job at creation and resolves it exactly like a direct reference, so the
worker receives the resolved token. This is the shape the Credentials Manager epic stores a
credential in.

`apiToken` is already in the cache from the earlier steps, so this job is activated with the value
on the **first** call rather than being parked first. That is the same cache the resolve endpoint
reads, so a value fetched once serves both paths until the TTL expires.

## Reference

Configuration (all under `camunda.secrets`, overridable per physical tenant under
`camunda.physical-tenants.<id>.secrets`):

- `stores.file.default.path` - directory backing the file store. Default `/etc/camunda/secrets`.
- `cache.ttl` - how long a resolved value is served from cache. Whole minutes, minimum `1m`,
  default `20m`.
- `cache.max-size` - entries per store cache. Default `1000`.
- AWS Secrets Manager (`stores.aws.default.*`) and GCP Secret Manager (`stores.gcp.default.*`) are
  supported too, with identity-based authentication only. Exactly one store per physical tenant.

API:

- `POST /v2/secrets/resolve` - body `{"references": ["camunda.secrets.<name>", ...]}`, max 20
  references, deduplicated server-side. Always HTTP 200 for a valid request; per-reference
  outcomes in `resolved` and `errors` (`ACCESS_DENIED`, `NOT_FOUND`, `INVALID_REFERENCE`).
  Requires `SECRET:REVEAL` per reference.
- `POST /v2/secrets/list` - body `{}`. Returns reference names the caller holds `SECRET:READ` on.
- Both are also reachable per physical tenant at `/physical-tenants/{id}/v2/secrets/...`.
- Both are marked alpha in 8.10.

Authorizations: resource type `SECRET`, permissions `REVEAL` (resolve) and `READ` (list). The
resource id is the whole reference (`camunda.secrets.apiToken`) or `*`.

## Reset between runs

`run-demo.sh` does this for you. These commands are only for the manual walkthrough.

Nothing is cleaned up on stop: the H2 secondary storage is file-based, the Zeebe data directory
persists, and the secrets directory stays populated. A second run over that state fails on the
already-existing users and skips the incident in step 6, so reset before each run:

```bash
cd c8run
./c8run stop
rm -rf camunda-data camunda-zeebe-8.10.0-SNAPSHOT/data
rm -f log/*.log
rm -rf ../secret-demo/secrets
../secret-demo/scripts/01-create-secrets.sh
```

`camunda-data` is the H2 database and `camunda-zeebe-*/data` is the Zeebe partition data. Clear the
log *files* but keep the `log` directory: c8run opens `camunda.log` without creating it, so
removing the directory makes the next start fail.

To remove the demo entirely, also delete the extracted `c8run/camunda-zeebe-8.10.0-SNAPSHOT/`
directory and its tarball.
