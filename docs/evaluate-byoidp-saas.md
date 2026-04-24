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

## Recommendation

**Approach A is the clear MVP.** The multi-OIDC support from `bfe2316a` does the hard part
already. The remaining work is entirely in the Controller and Console — secret storage, env-var
propagation, and a UI form. No changes are needed in this repository for the basic flow.

The key architectural insight is that **Auth0 and the customer IdP coexist as dual providers**.
This preserves all existing SaaS infrastructure (Controller M2M, Web Modeler, Connectors, incident
notifier) while giving customers their own login path. The restart cost is real but bounded
(rolling restart), and the provider-selection login page is a standard pattern users expect from
enterprise SSO setups.

If "losing the Auth0 connection" is truly acceptable for certain customers, Approach B becomes
viable but requires significantly more work to replace the M2M communication layer. It could be a
Phase 2 option offered to customers who explicitly want Auth0 removed.

Approach C (dynamic reload) is a nice-to-have optimization that could come later but is not
justified for an MVP given the complexity.
