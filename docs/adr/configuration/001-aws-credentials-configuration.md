# Per-consumer AWS credentials configuration

**DRI**: Data Layer team

**Status**: Draft (8.10)

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

### D1. A new `aws` section

AWS credential configuration lives in a new `aws` section (embedded on each consumer's
config block, see D3), this allows:

- **Per-tenant for free.** Because per-tenant resolution is a two-bind over the
  whole `Camunda` root, `camunda.physical-tenants.<id>.<...>.aws.*` works with zero
  new overlay code. The section is a flat POJO (no typed maps), so no
  `MapOverlaySpec` registry entry is needed.
- **One seam.** Consumers stop constructing their own
  `DefaultCredentialsProvider`; they receive a credentials provider derived
  from their (tenant-scoped) `Camunda` instance.

### D2. Shape: static credentials or web identity (IRSA), plus region

```yaml
...
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
Credentials, this is the current behaviour and thus maintains backwards compatibility.

### D3. Inline blocks for secondary storage AWS config

Each secondary storage or document store which talks to AWS will get its own `aws` block, this
Matches the approach for backup store for primary storage. Making it clear that **credentials live on the config block
Of the thing they authenticate.** This clarity means that there is no “silent” inheritance of settings from a place that
Can’t be seen while looking at the component config (as the default provider chain currently does). This avoids issues
Like source of truth confusions when debugging (what config is actually applied), incidental damage from a config change
To an inherited value.

```yaml
camunda:
  data:
    secondary-storage:
      opensearch:
        url: https://my-domain.eu-west-1.es.amazonaws.com
        aws:                          # NEW — credentials for signing OS requests
          role-arn: arn:aws:iam::111:role/camunda-os
          web-identity-token-file: /var/run/secrets/token
      rdbms:
        url: jdbc:aurora://...
        username: camunda             # DB user/password — already exist, untouched
        password: ...
        aws:                          # NEW — the IAM identity used to mint
          role-arn: ...               # Aurora IAM auth tokens (future work)
  document:
    aws:
      invoices:                       # per-STORE, already decided in #54366
        bucket: tenant-a-invoices
        aws-access-key-id: ...
```

### D4. Per tenant overrides

This solution now enables PT config overrides at no additional cost, through the double bind of configuration an scalar property is
Allowed to be overridden so the following is perfectly valid:

```yaml
camunda:
  data:
    secondary-storage:
      opensearch:
        aws:
          role-arn: arn:aws:iam::111:role/default-tenant-os   # default tenant

  physical-tenants:
    tenantb:
      data:
        secondary-storage:
          opensearch:
            url: https://tenant-b-domain...
            aws:
              role-arn: arn:aws:iam::222:role/tenant-b-os     # tenant B's role
              web-identity-token-file: /var/run/secrets/tenant-b-token
```

There is a trade off that if one tenant is using the same IAM for secondary storage and the document store,
The credentials will need to be written twice, an acceptable cost to avoid source of truth issues.

## Consequences

- Per-physical-tenant AWS credentials become configurable with a small flat
  `aws` block embedded on each AWS consumer's config, and no new overlay
  machinery.
- Existing env-var-based deployments (`AWS_ACCESS_KEY_ID` etc.) keep working
  untouched — the default chain remains the fallback at every level.

## Alternatives considered

- **Under `camunda.data.secondary-storage.aws.*`**: closer, but still couples
  credentials to secondary storage; the document store (`camunda.document.*`)
  and primary-storage backup are not secondary storage and could not reuse it
  without reaching across sections.
- **Named credential profiles** (`camunda.aws.profiles.<name>` referenced by
  consumers): the most flexible shape and a natural fit for the
  `MapOverlaySpec` engine, but speculative today — no current requirement for
  multiple IAM identities within one tenant (YAGNI). The inline per-consumer
  blocks (D3) keep the door open without the map.
- **Status quo (env vars / instance profile only)**: the default chain is
  process-global, so per-tenant identities are impossible — this is exactly
  the blocker reported in #53493/#53494.

