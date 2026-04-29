# Pragmatic Evaluation: Bring Your Own IdP (BYOIDP) in SaaS

## Context

This document evaluates approaches to enable SaaS customers to configure their own Identity
Provider (IdP) for Camunda 8 Orchestration Clusters. The evaluation leverages the existing
multi-OIDC provider support added in commit `bfe2316a` (feat: support multiple OIDC providers).

- Epic: [camunda/product-hub#3190](https://github.com/camunda/product-hub/issues/3190)
- Technical breakdown:
  [Google Doc](https://docs.google.com/document/d/1Nc2Uhhjfle69SZuN_DZByQ9duKERMxdK87oWsF-rnP4/edit)

## What Already Exists

Commit `bfe2316a` added full multi-OIDC provider support to the `authentication/` and `security/`
modules. It already handles:

- **Multiple providers via config:**
  `camunda.security.authentication.providers.oidc.<name>.*` — each entry gets its own
  `OidcAuthenticationConfiguration`, merged with the legacy single-provider path.
- **Issuer-aware JWT decoding:** Bearer tokens are routed to the correct provider by their `iss`
  claim via `IssuerAwareJWSKeySelector` and `IssuerAwareTokenValidator`.
- **Multi-provider login page:** Spring's default login page lists all registered providers; users
  pick one.
- **Per-provider token validation:** audience, organization, and cluster validators are applied
  per-registration via `TokenValidatorFactory`.
- **Full backward compatibility:** the legacy single `oidc.*` path still works alongside the new
  `providers.oidc.*` map.

The SaaS validators (`OrganizationValidator`, `ClusterValidator`) already handle missing claims
gracefully — if the custom claim (`https://camunda.com/orgs`, `https://camunda.com/clusterId`) is
absent from a token, validation passes. This means tokens from a customer IdP that don't carry
Camunda-specific claims will not be rejected by these validators.

### Configuration is static

The current OIDC configuration lifecycle is fully static — all provider registrations are read once
at startup:

- `OidcAuthenticationConfigurationRepository` populates a `final` map in its constructor
- `InMemoryClientRegistrationRepository` is immutable
- `JwtDecoder`, `IssuerAwareJWSKeySelector`, and `IssuerAwareTokenValidator` all hold immutable
  snapshots

Adding or changing a provider requires a pod restart.

## Approaches (Least to Most Work)

### Approach A: Restart-based config injection (MVP — least work)

**How it works:**

1. Customer provides their OIDC settings (issuer URL, client ID, client secret) through Console UI.
2. Controller stores the config (K8s secret as described in the technical breakdown's appendix, or
   vault).
3. Controller sets additional environment variables on the cluster pods:
   ```
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_CUSTOMER_ISSUERURI=https://customer.okta.com
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_CUSTOMER_CLIENTID=abc123
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_CUSTOMER_CLIENTSECRET=<from secret>
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_CUSTOMER_CLIENTNAME=Customer SSO
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ANOTHERIDP_ISSUERURI=https://customer.okta.com
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ANOTHERIDP_CLIENTID=abc123
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ANOTHERIDP_CLIENTSECRET=<from secret>
   CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ANOTHERIDP_CLIENTNAME=Customer SSO
   ```
4. Cluster pods restart. The existing multi-OIDC code picks up both Auth0 + customer IdP
   automatically.
5. Auth0 stays as a provider (keeps Controller M2M, internal services, and support admin access
   working).
6. Customer users see a login page with two options: Auth0 and their own IdP.

**What needs to be built:**

- Console UI: a form to collect OIDC settings (issuer URL, client ID, client secret, scopes).
- Controller: store the secret, add env vars to cluster spec, trigger a rolling restart.
- Validation: a "test connection" call before applying (OIDC discovery fetch +
  `client_credentials` grant attempt).

**What needs NO changes:**

- Zero changes to the `authentication/` module — multi-OIDC already works.
- Zero changes to `security/` — SaaS validators tolerate missing custom claims.
- Zero changes to Web Modeler or Connectors — they keep using Auth0 M2M tokens, and the cluster
  accepts them because Auth0 remains a registered provider.
- Zero changes to the Zeebe engine, exporters, or any other component.

**Tradeoffs:**

- Requires a cluster restart when IdP config is added/changed/removed (rolling restart minimizes
  downtime).
- Users see a provider selection page instead of being auto-redirected (acceptable for MVP; could
  be improved later with a default-provider hint).
- Auth0 remains visible as a login option (could be hidden via UI customization later, or accepted
  as the "admin/support" entry point).

**Estimated scope:** Console UI + Controller secret storage + Controller env-var propagation +
restart trigger. Most of the heavy lifting is on the Controller/Console side, not in this
repository.

### Approach B: Replace Auth0 entirely with customer IdP (medium work)

**How it works:**

1. Same Console UI flow as Approach A.
2. Instead of adding a second provider, the customer IdP **replaces** Auth0 as the sole OIDC
   config.
3. The cluster is reconfigured with only `camunda.security.authentication.oidc.*` pointing to the
   customer IdP.
4. Cluster restarts with just the customer IdP.

**What breaks:**

- **Controller M2M access:** The Controller uses Auth0 `client_credentials` tokens to communicate
  with clusters. Those tokens would be rejected because the cluster no longer trusts the Auth0
  issuer. The Controller would need to either:
  - Obtain tokens from the customer's IdP (requires the customer to provision an M2M client for
    Camunda — complex, security-sensitive).
  - Use a non-OIDC auth mechanism (API key, mTLS, service mesh identity) — requires new work.
- **Web Modeler deployments:** Currently uses Auth0 tokens — would need the same alternative.
- **Connectors:** Same issue.
- **Incident notifier:** Uses Auth0 M2M tokens to call Console — breaks.
- **SaaS validators:** `OrganizationValidator` and `ClusterValidator` check Camunda-specific claims
  that only Auth0 tokens carry. Customer IdP tokens won't have them. The validators tolerate
  missing claims, but this means the organization/cluster scoping is lost — any valid token from
  the customer IdP would be accepted regardless of org/cluster context.

**What needs to be built (beyond Approach A):**

- Alternative auth mechanism for Controller-to-cluster communication.
- Web Modeler and Connectors auth rework.
- Decision on whether to keep or remove SaaS validators.

**When this makes sense:** Only if the customer has a hard requirement that Auth0 must not be
present at all (regulatory, security policy).

### Approach C: Dynamic reload without restart (most work)

**How it works:** Same as Approach A, but the OIDC provider is added at runtime without restarting
the cluster.

**What needs to be built:**

- Mutable `ClientRegistrationRepository` (replacing Spring's immutable
  `InMemoryClientRegistrationRepository`).
- Mutable `OidcAuthenticationConfigurationRepository` (currently `final` map, populated once in
  constructor).
- Rebuild or make dynamic: `JwtDecoder`, `IssuerAwareJWSKeySelector`,
  `IssuerAwareTokenValidator` (all hold immutable snapshots).
- A management REST API or config-distribution channel (Zeebe gossip protocol extension, or a
  dedicated endpoint) for the Controller to push OIDC configs.
- Thread-safety across all of the above.
- Dynamic `SecurityFilterChain` reconfiguration.

**Tradeoffs:**

- Best UX (zero downtime).
- Significant engineering effort across `authentication/`, `security/`, and the config
  distribution layer.
- Higher risk — dynamic security reconfiguration is notoriously tricky to get right.

## POC Validation

The dual-provider approach (Approach A) has been validated on development SaaS
(`console.dev.ultrawombat.com`). By creating a new generation with the following additional
environment variables, an Orchestration Cluster can be started that presents both Auth0 and a
custom Entra IdP as login options:

```
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_ISSUERURI=https://login.microsoftonline.com/ea952511-6a7f-40b8-af71-6c1be6f0dade/v2.0
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_CLIENTID=6fe432f2-7dc3-4e13-b1b4-4cc8480c80b7
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_CLIENTSECRET=<entra-client-secret>
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_CLIENTNAME=EntraSSO
```

After logging out of the SaaS OC cluster started from such a generation, the login page presents a
choice between the default Auth0 provider and the EntraSSO provider. No code changes to the
`authentication/` or `security/` modules were required — the existing multi-OIDC support from
commit `bfe2316a` handled everything.

**Note:** The redirect URI env var (`REDIRECTURI`) was intentionally omitted. The default
`{baseUrl}/sso-callback` is used, which Spring expands from the actual request. The corresponding
redirect URI must be registered in the Entra app registration.

## POC Findings: Logout Behavior

Testing the dual-provider POC on `dev.ultrawombat.com` revealed logout issues that would need to be
addressed before shipping.

### Auth0 logout (broken)

After logging out via Auth0, the user is redirected to
`https://weblogin.cloud.dev.ultrawombat.com/u/login/identifier` which shows an error page
("Oops!, something went wrong"). This is an **Auth0 tenant configuration issue**, not a code bug.

The logout flow in the OC:
1. `CamundaOidcLogoutSuccessHandler` (extends Spring's `OidcClientInitiatedLogoutSuccessHandler`)
   resolves the `end_session_endpoint` from the `ClientRegistration` matching the user's auth token
2. It sets `post_logout_redirect_uri={baseUrl}/post-logout`
3. Auth0 receives this but redirects to its own login page with an error instead of honoring the
   `post_logout_redirect_uri`

The likely cause is that the cluster's `/post-logout` URL is not registered in Auth0's "Allowed
Logout URLs" for the application. This is configured in the Auth0 tenant settings, managed outside
the `camunda/camunda` repo (likely in infra/Terraform configuration in the Camunda org).

### Entra logout (works)

After logging out via MS Entra, the user is correctly redirected to
`https://lpp-1.api.dev.ultrawombat.com/68f6b638-236c-492f-a198-afccaf6727a4/login` (the cluster
login page). Entra properly honors the `post_logout_redirect_uri` and the `PostLogoutController`
redirects back to the login page.

### Multi-provider logout gap: `idpLogoutEnabled` is not per-provider

The `oidcLogoutSuccessHandler` bean in `WebSecurityConfig.java` checks `isIdpLogoutEnabled()` only
on the top-level `camunda.security.authentication.oidc` config, not per provider under
`providers.oidc.<name>`. In a multi-provider setup, there is no way to disable IdP-side logout for
one provider while keeping it for another. This is a gap that may need addressing if BYOIDP ships
— for example, a customer might want IdP-side logout for their own provider but not for Auth0 (or
vice versa).

## IdP Management: Where Should It Live?

The MVP (Approach A) requires a UI where customer admins configure their IdP settings and a backend
that stores and propagates them. There are three placement options, all variants of Console-managed
configuration. Managing IdP config inside the OC Admin UI (making OIDC config dynamic at runtime)
is not pursued — the authentication layer in the OC is being replaced by
[camunda-security-library](https://github.com/camunda/camunda-security-library), so investing in
making the current `authentication/` module's static config dynamic would be throwaway work.

### Option 2a: Console per-cluster settings

Customer admins configure IdP settings per cluster in Console. The Controller stores the config and
injects it as env vars when the cluster (re)starts.

- **Pros:** Granular control per cluster. Natural fit for Console's existing cluster management
  surface. Different clusters can use different IdPs.
- **Cons:** Must be configured for each cluster individually. Restart required per cluster.

### Option 2b: Console per-org settings (applied to all clusters)

Customer admins configure IdP settings once at the organization level. The Controller applies the
same custom IdP env vars to every cluster in the org.

- **Pros:** Simplest UX — configure once, applies everywhere. Matches the common case where an
  enterprise uses a single IdP.
- **Cons:** No per-cluster flexibility. Adding/changing the IdP requires restarting all clusters in
  the org.

### Option 2c: Console per-org settings with per-cluster override

Combination: org-level default IdP inherited by all clusters, with the ability to override or
disable on specific clusters.

- **Pros:** Best of both — simple default with escape hatch for edge cases.
- **Cons:** Most complex Console/Controller implementation. UI must clearly communicate inheritance
  and overrides.

### Implementation in Console (`camunda-cloud-management-apps`)

The Console codebase (`camunda/camunda-cloud-management-apps`) is a pnpm monorepo. The cluster
settings page where IdP configuration would be added follows an established pattern:

**Frontend (per-cluster settings tab):**
- Settings tab: `packages/c4/src/widgets/cluster-details/tabs/c4-cluster-details-settings-tab.tsx`
- Settings UI: `packages/c4/src/components/c4-cluster-settings/c4-cluster-settings.tsx`
- Individual elements: `packages/c4/src/components/c4-cluster-settings/elements/` (one file per
  setting — e.g., `mcp-toggle.tsx`, `swagger-toggle.tsx`)
- A new `idp-configuration.tsx` element would contain a form for issuer URL, client ID, client
  secret, and display name

**Backend (cluster update flow):**
- Router: `apps/console-backend/src/routes/ClusterRouter.ts` — `PATCH /:clusterId` validates and
  dispatches cluster setting changes
- Cluster entity: `apps/console-backend/src/entities/Cluster.ts` (TypeORM) — non-secret IdP
  fields (issuer URL, client ID, display name, enabled flag) go in a JSON column
- K8s spec generation: `apps/console-backend/src/controller/k8s/k8s-spec.utils.ts` —
  `generateSpec()` builds `overrideEnv` arrays that the Controller applies to pod specs

**How configuration reaches cluster pods:** Console-backend writes a `ClusterSpec` object
(including `overrideEnv` entries) to a `ZeebeCluster` CRD via the K8s API. The Controller
(a separate K8s operator in `camunda/camunda-operator`) watches these CRDs and applies the env
vars to the cluster pod specs. This is the same mechanism used for all existing cluster settings.

### Client secret handling

The IdP client secret must not be stored in plaintext. The
[Secret Management epic](https://github.com/camunda/product-hub/issues/3040) (currently in Define
phase) is designing a secret store abstraction for Camunda, but its focus is on connector/process
execution secrets rather than infrastructure configuration secrets. Its timeline (8.10 or 8.9
stretch) and scope make it unlikely to be directly reusable for BYOIDP in the short term.

The Console codebase already has a mature pattern for runtime secrets via **connector secrets**:
- Stored in **AWS Secrets Manager** and **GCP Secret Manager** (dual-provider, based on cluster
  hosting region)
- Implementation: `apps/console-backend/src/controller/connectorSecrets.controller.ts`
- Secret values are **never stored in the database** and **never returned to the frontend** —
  they are masked as `"XXX"` when listing
- Named by convention: `connector-secrets-{clusterId}`

The recommended approach for the IdP client secret is to **follow this same pattern**:
- Store in AWS SM / GCP SM with a name like `idp-config-{clusterId}`
- When generating the K8s spec, fetch the secret from the secret manager and emit it as an
  `overrideEnv` entry (`CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_CUSTOM_CLIENTSECRET`)
- Mask the value in all API responses to the frontend

This is more consistent with how the Console repo already operates than the K8s Secret approach
proposed in the original technical breakdown. It requires no RBAC changes or cross-namespace
secret access, and reuses existing cloud secret manager infrastructure.

If #3040 later delivers a general-purpose secret store abstraction, the IdP client secret storage
could migrate to it — but this is not a prerequisite for the MVP.

## Implementation Plan

The following decisions drive the implementation:

### Iterative approach

The implementation will be iterative, starting with an MVP and adding layers on top. The MVP
focuses on getting custom IdPs working on SaaS clusters with minimal changes; polish (validation,
UX refinements, org-level config) comes in later iterations.

### Per-cluster IdP configuration

IdP configuration will target **individual clusters** (Option 2a), leveraging the existing
multi-OIDC config feature. The multi-provider map
(`camunda.security.authentication.providers.oidc.<name>.*`) already supports an arbitrary number
of named providers, so the implementation will support **multiple custom IdPs per cluster**, not
just one.

### UI flow

- IdP configuration will be added to **cluster settings** in Console. The exact UI flow will be
  discussed separately.
- When entering a cluster, the customer will see a **provider selection page** where they choose
  which IdP to authenticate with.
- An additional **toggle in cluster settings** will allow enabling/disabling authentication via
  Camunda Auth (Auth0) on the cluster. When disabled, only the customer's IdPs will be available
  for login. Note: disabling Auth0 for user login does not affect M2M access — Auth0 M2M tokens
  from the Controller, Web Modeler, and Connectors will continue to work as they are validated
  separately via the issuer-aware JWT decoder.

### Logout behavior (requires investigation)

When a customer IdP is already federated via Auth0 (e.g., the same Entra tenant is connected to
both Auth0 and configured directly on the cluster), we need to verify what happens on logout:
- Logging out from the cluster must not log the user out of the IdP completely.
- Logging out from the cluster must not log the user out of Console.
- This requires access to the Auth0 tenant for testing.

### Validation strategy

Config validation will **not** be part of the first version. In the MVP, the customer will use
the existing pattern of OIDC configuration for OC — if misconfigured, this will cause failures
and an unhealthy cluster, consistent with how other cluster configuration errors behave today.
Validation (OIDC discovery fetch, client_credentials grant test, claim mapping checks) will be
added in a later iteration.

## Recommendation

**Approach A is the clear MVP.** The multi-OIDC support from `bfe2316a` does the hard part
already. The remaining work is entirely in the Controller and Console — secret storage, env-var
propagation, and a UI form. No changes are needed in the OC for the basic flow.

The key architectural insight is that **Auth0 and the customer IdP coexist as dual providers**.
This preserves all existing SaaS infrastructure (Controller M2M, Web Modeler, Connectors, incident
notifier) while giving customers their own login path. The restart cost is real but bounded
(rolling restart), and the provider-selection login page is a standard pattern users expect from
enterprise SSO setups.

If "losing the Auth0 connection" is truly acceptable for certain customers, the Auth0 toggle
provides a path to effectively hide Auth0 from end users while keeping M2M access intact.

Approach C (dynamic reload) is a nice-to-have optimization that could come later but is not
justified for an MVP given the complexity. The upcoming
[camunda-security-library](https://github.com/camunda/camunda-security-library) migration may
revisit dynamic provider registration as part of its design.
