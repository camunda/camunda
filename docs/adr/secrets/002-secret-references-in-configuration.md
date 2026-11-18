# Secret references in configuration: a store of their own, resolved per tenant and refreshed on every node

**DRI**: Matthew Kavanagh

**Status**: Proposed (8.11)

**Deciders**

- Matthew Kavanagh
- Carlo Sana

**Purpose**: Record who can write a secret reference in the configuration, which store it is read from and how its value is refreshed while a node runs, and how this differs from the references in processes that [secrets ADR 001](001-central-secret-resolution-architecture.md) describes.

**Audience**: Engineers working on the configuration classes, the secret stores or a client that takes credentials from the configuration; operators setting up credential rotation.

## Context

The orchestration cluster reads the credentials it uses to reach secondary storage, the document and backup stores and the identity provider only on startup, so rotating them means restarting every pod. The goal of the dynamic secrets epic ([camunda/camunda#69878](https://github.com/camunda/camunda/issues/69878)) is that an operator changes a credential in their secret store and the running cluster picks it up within a set interval (without any pods restarting, and without any changes to the Camunda configuration).

Processes can already refer to secrets, but those references and their store are built for a different reader:

- A process reference is written by whoever deploys a process, and resolved for the job worker that receives the job. Any process can reference any secret in the tenant's store.
- Its values sit behind a cache whose TTL bounds how stale a value can be, and values aren't read again until they expire.

## Decision

### D1. A reference is a value of exactly `secret:<name>`

A configuration value is a reference when it is exactly `secret:<name>`, where the name follows the same rules as in processes. Any other value is a literal, including one that contains `secret:` further in. We don't use `camunda.secrets.<name>`, the form processes use: it looks like a property name, and `camunda.secrets.*` already holds the secret store configuration. A Spring placeholder such as `${...}` would be resolved by Spring as a property.

### D2. Only the operator writes references, and only in properties marked to accept them

References are written in configuration (and later through the tenant lifecycle API), so whoever can write a reference can already write the credential itself. Only properties marked as credentials in configuration classes accept a reference. If a reference is malformed, in a property that isn't marked as a credential, or belongs to a tenant without a configuration secrets store, we will fail on startup with a message identifying the property and tenant (if any).

Properties whose clients will take new values at runtime are the credentials of secondary storage, exporters, the backup and document stores and the OIDC client. Other credentials will accept secret references too, but we only resolve them on startup (e.g. TLS key store passwords, the license key, the incident notifier's client secret and the passwords of initial users). Changes to any of those are picked up at the next restart.

### D3. Configuration secrets have a store of their own

Each physical tenant can configure one configuration secrets store, under `camunda.secrets.internal-config.<type>`, with the same store types and settings as the stores under `camunda.secrets.stores`. A tenant that configures its own under `camunda.physical-tenants.<id>` replaces the root's as a whole, so it can use another type.

```yaml
camunda:
  secrets:
    stores:
      aws:
        default:
          region: eu-west-1
          path-prefix: camunda/jobs/
    internal-config:
      file:
        path: /etc/camunda/secrets
```

The store isn't part of the tenant's `SecretStoreRegistry`, so neither the engine nor `/v2/secrets/resolve` can read from it. Startup fails if the scope (a directory, a name prefix, a container secret) of a job store of the same type overlaps with it. This covers the job stores of every tenant.

A separate store will also let the Helm chart set up a file store for configuration secrets regardless of whatever store jobs use (and move credentials it supplies into the file store on upgrade).

The store has no cache. It is read once on startup and then once per refresh (D5).

### D4. Each tenant's configuration is resolved in place on startup

Each tenant's references are resolved as soon as its configuration is bound, and the values are written into the tenant's `Camunda` object, so every consumer reads them as it reads a literal today. For the root configuration, this happens before any other bean can read it. We keep the reference behind each resolved property, so that the refresh task can read it again and a client can resolve it again when it builds a new client.

A tenant whose store can't be read will start with a degraded status ([camunda/camunda#69883](https://github.com/camunda/camunda/issues/69883)).

Clients that can take new values (e.g. secondary storage) will instead get them through the refresh path (D5), starting from a client that isn't ready until its references first resolve, so that startup, a rotation and an unreadable store all take the same path.

### D5. Each node refreshes each tenant's references on its own

Each node runs a refresh task per tenant that reads every reference at the tenant's refresh interval (`camunda.secrets.refresh-interval`, 5 minutes by default and at least 30 seconds). It also compares the content of the files that a client reads from the configuration, since Kubernetes replaces a mounted Secret at the same path. A changed value is passed on to the clients that use it, through one interface that each client implements:

1. Build a new client from the new values, with a timeout, while the old client keeps serving.
2. Check the new client against the real service.
3. Send new work to the new client.
4. Close the old client once its running work has finished, or after a timeout the client sets.

If the build or the check fails, the new client is closed and the old one stays in use. A failed read keeps the current value too, including a secret that the store reports as missing or forbidden, and is retried with exponential backoff from 10 seconds up to the refresh interval.

### D6. By default, literal credentials are reported, not rejected

On startup, if we see a credential property that holds a literal we will log a warning identifying the property (and the tenant, if any - but not the value!). We will also provide a setting to fail on startup instead, but this will be off by default to enable upgrades. Enabling this setting will be required for some new physical tenants functionality (dynamic updates to tenant configuration).

### D7. Failures are reported per reference and client, never with the value

Each applied change and each failure will be logged (one line each). The log message will identify (where applicable) the tenant, the property, the reference, and the client - but never the value. For failing refreshes, we will set a gauge per tenant, reference and client (with the reason), and a second metric will record when each node last applied a change to each reference so that operators can check that every node has switched before revoking an old credential. A failing refresh doesn't change the tenant's health status, but its health details list the failing references.

## Consequences

- Operators will have to configure a second store to make use of this functionality (except where the Helm chart sets it up for them).
- For up to one refresh interval, nodes (and the clients within one node) can use different values of the same reference.
- A client holds two connections while it switches, so a database or search cluster close to its connection limit can refuse the new one. The check then fails and the old client stays in use.
- A credential in an environment variable, or in a property that is only resolved on startup, still needs a restart to change.
- A reference in a legacy property (one that a configuration getter falls back to, such as `zeebe.broker.data.backup.s3.accessKey`) is neither checked nor resolved, since only the bound configuration is.

## Source

- [Dynamic secrets (camunda/camunda#69878)](https://github.com/camunda/camunda/issues/69878)
- [Secret references in configuration (camunda/camunda#69879)](https://github.com/camunda/camunda/issues/69879)
- [Dynamic Configuration & Secrets (camunda/product-hub#3816)](https://github.com/camunda/product-hub/issues/3816)
- [Solution proposal](https://github.com/camunda/pdp-initiative-pc-physical-tenants-at-scale/blob/8c75f65d4ae2cc349576f930ccd333f846aa3cdf/dynamic-secrets/solution-proposal.md)

