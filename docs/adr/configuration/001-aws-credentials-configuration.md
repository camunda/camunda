# Tenant-level AWS credentials configuration

**DRI**: Data Layer team

**Status**: Draft (8.10) — validated by spike (`spike/tenant-level-aws-credentials`)

**Purpose**: Decide where AWS credential properties live in the unified
configuration so that they can be set per physical tenant, are reusable by every
AWS consumer (OpenSearch, Aurora/RDBMS, document store, backups), and stay
backwards compatible with today's environment-variable-based setups.

**Audience**: Engineers working whoever AWS credentials is needed, including but not limited to:
Config, search layer, and document stores.

Relates to: camunda/camunda#53493 (per-PT AWS OpenSearch credentials),
camunda/camunda#53494 (per-PT AWS Aurora credentials),
camunda/camunda#54366 (per-tenant document store configuration), physical
tenants epic camunda/camunda#52027, and the "Document handling
Per-Physical-Tenant Isolation" page of the PT kickoff document.

## Context

Physical tenants (PT) can run with their own database and customers can use distinct AWS IAM
Identities per tenant, right now no AWS credentials can differ per PT as none of them read
from unified config, they all resolve through the AWS SDK default provider chain
(https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).
That chain is opaque: the effective identity is resolved from the process environment and is
not visible or documented anywhere in the application configuration.
This is fine for a single tenant but will not allow separate IAM roles for different PTs.

## Decision

### D1. A single tenant-level `aws` section

AWS credential configuration lives in one `aws` section directly under the tenant
root: `camunda.aws` for the default tenant and
`camunda.physical-tenants.<id>.aws` per physical tenant. Each tenant has exactly
one set of AWS credentials, shared by every AWS consumer of that tenant
(OpenSearch, Aurora/RDBMS, document store, backups). This allows:

- **Per-tenant for free.** Because per-tenant resolution is a two-bind over the
  whole `Camunda` root, `camunda.physical-tenants.<id>.aws.*` works with zero
  new overlay code. The section is a flat POJO (no typed maps), so no
  `MapOverlaySpec` registry entry is needed.
- **One seam.** Consumers stop constructing their own
  `DefaultCredentialsProvider`; they receive a credentials provider derived
  from their (tenant-scoped) `Camunda` instance
  (e.g. `physicalTenantResolver.mapValues(Camunda::getAws)`).
- **One identity per tenant.** A tenant cannot configure different IAM
  identities for, say, secondary storage vs the document store — the same
  constraint as today without physical tenants, and there is no current
  requirement for more (YAGNI).

### D2. Shape: static credentials or web identity (IRSA), plus region

```yaml
camunda:
  aws:
    access-key:                # optional (static credentials)
    secret-key:                # optional (static credentials)
    session-token:             # optional (static credentials)
    role-arn:                  # optional (web identity / IRSA)
    web-identity-token-file:   # optional (web identity / IRSA)
    region:                    # optional
```

The credentials configured are turned into an AWS SDK `AwsCredentialsProvider`, however
Crucially if everything is left empty then the SDK default provider chain is used to retrieve
Credentials, this is the current behaviour and thus maintains backwards compatibility: a
deployment with no `camunda.aws` block anywhere keeps resolving its identity from
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, instance profiles, IRSA env vars, etc.,
exactly as today.

### D3. Per tenant overrides

Each physical tenant can declare its own `aws` block under its tenant root:

```yaml
camunda:
  aws:
    role-arn: arn:aws:iam::111:role/default-tenant       # default tenant

  physical-tenants:
    tenantb:
      aws:
        role-arn: arn:aws:iam::222:role/tenant-b         # tenant B's identity
        web-identity-token-file: /var/run/secrets/tenant-b-token
```

Overrides merge at field level: a tenant that sets only `role-arn` inherits the
remaining fields (e.g. `region`) from the root block.

### D4. Omitting `aws` on a tenant means inheriting the root block

A physical tenant that declares no `aws` block silently inherits the default
tenant's `camunda.aws` block in full — the standard two-bind semantics, and we
accept them for credentials too. This is consistent with every other inherited
section of the unified configuration, and the alternative (forcing every tenant
to restate credentials, as `PhysicalTenantRequiredOverrideValidation` does for
`security.initialization`) would punish the common case where all tenants
legitimately share one IAM identity. Operators who need per-tenant identities
declare them explicitly; the resolved per-tenant configuration makes the
effective identity visible, which the SDK default chain never did.

If no `camunda.aws` block is configured at all, every tenant resolves an empty
section and falls back to the SDK default provider chain (see D2).

## Consequences

- Per-physical-tenant AWS credentials become configurable with a single small
  flat `aws` block per tenant, and no new overlay machinery.
- All AWS consumers of a tenant share one IAM identity — no per-consumer
  differentiation, matching today's single-tenant behaviour.
- Existing env-var-based deployments (`AWS_ACCESS_KEY_ID` etc.) keep working
  untouched — the default chain remains the fallback when nothing is
  configured.
- Each AWS consumer needs a one-time change to accept an injected credentials
  provider instead of building its own default chain (OpenSearch connector
  done in the spike; document store, S3 backup store, legacy connectors
  outstanding).

## Alternatives considered

- **Inline `aws` blocks per consumer** (on `opensearch`, `rdbms`): makes credentials visible
  on the config block of the thing they authenticate and would allow different
  IAM identities per consumer within one tenant. Rejected: no current
  requirement for multiple identities per tenant (same as today without PT),
  and duplicating the same credentials across several blocks creates
  source-of-truth drift; a single tenant-level block is simpler.
- **Under `camunda.data.secondary-storage.aws.*`**: couples credentials to
  secondary storage; the document store (`camunda.document.*`) and
  primary-storage backup are not secondary storage and could not reuse it
  without reaching across sections.
- **Named credential profiles** (`camunda.aws.profiles.<name>` referenced by
  consumers): the most flexible shape and a natural fit for the
  `MapOverlaySpec` engine, but speculative today — no current requirement for
  multiple IAM identities within one tenant (YAGNI).
- **Required per-tenant `aws` declaration** (fail fast when a tenant omits it,
  like `security.initialization`): safer against a forgotten tenant
  authenticating as the default tenant, but rejected in favour of standard
  inheritance (D4) — restating shared credentials for every tenant is the
  common case and the resolved config makes the effective identity auditable.
- **Status quo (env vars / instance profile only)**: the default chain is
  process-global, so per-tenant identities are impossible — this is exactly
  the blocker reported in #53493/#53494.

