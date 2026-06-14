# Epic Breakdown: Bring Your Own IdP per Cluster in SaaS

## Status

- **Epic:** [camunda/product-hub#3190](https://github.com/camunda/product-hub/issues/3190)
- **Updated product brief:
  ** [Google Doc](https://docs.google.com/document/d/1Erwa5erxpO5oWBW7b3SQXr_xD2CcCzIl7uiJfg_X5VE)
- **User journey:
  ** [Figma](https://www.figma.com/board/CM3QV2uOSqWe8fjTaWKOGk/Bring-your-own-Identity-Provider-per-Cluster-in-SaaS)
- **Companion document (historic):** [`docs/evaluate-byoidp-saas.md`](./evaluate-byoidp-saas.md) —
  POC notes,
  approach evaluation, and findings from the dev SaaS validation. Read it for *why* this plan picks
  Approach A; this document focuses on *what is delivered*.

## Purpose of this document

This breakdown turns the BYOIDP epic into a phased plan that names, per component, the work each
phase requires. It is the basis for filing feature tickets. It is **not** a design specification —
each phase's per-component design will be produced in a follow-up session against this plan.

This document is written for human readers (Product, Engineering Leads, PMs reviewing scope). A
separate technical handover document for AI agents picking up implementation will follow if needed.

## Approach summary

This plan implements **Approach A** from the POC: a dual-provider, restart-based, per-cluster
configuration model.

- The customer's IdP is registered as an *additional* OIDC provider on the cluster alongside
  Auth0. Auth0 remains trusted so Controller M2M, Web Modeler M2M, Connectors, support engineers,
  and other SaaS-internal flows keep working unchanged.
- Configuration lives **per cluster** (org-wide IdP configuration is out of scope of this epic).
- Configuration is propagated through the existing Console → `ZeebeCluster` CRD → Operator
  channel, just like the Authorizations and Multi-Tenancy toggles. Client secrets live in AWS
  Secrets Manager / GCP Secret Manager (matching the existing `connector-secrets-{clusterId}`
  pattern), not in the Console database or Kubernetes Secrets.
- The multi-OIDC plumbing was added to the OC in commit `bfe2316a` and now lives in the
  `camunda-security-library` (CSL), which the OC consumes as a dependency. CSL is the central
  authority for authentication; new auth-layer code lands in CSL, not in the OC's `authentication/`
  module.

Approaches B (replace Auth0 entirely) and C (dynamic reload without restart) were considered and
rejected during the POC. See the historic doc for reasoning.

## Architecture and repo split

The propagation chain mirrors how existing cluster settings flow:

```
Customer admin → Console UI → Console DB + AWS SM/GCP SM → ZeebeCluster CR
                                                              │
                                                              ▼
                                                       Operator (CRD watch)
                                                              │
              ┌───────────────────────────┬───────────────────┴───────────────────┐
              ▼                           ▼                                       ▼
   Connectors deployment        Unified Camunda app (pod)                  Camunda gateway (pod)
   (per-phase env wiring)       (envs read by CSL OIDC config)             (envs read by gateway
                                                                            config, mirroring app)
```

### Responsibilities

- **`camunda-cloud-management-apps` (Console)** — orchestrator. Owns the customer-facing UI, the
  source of truth for IdP configuration in Console DB, secret storage in AWS SM / GCP SM, the
  `proxy-token-issuer` app, and writes to the `ZeebeCluster` CR. Largest share of work across
  every phase.

- **`camunda-operator` (Operator)** — CRD owner. Adds typed identity fields, watches the CR,
  renders the corresponding `CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_*` (and Connectors
  client-auth) environment variables on the unified Camunda app, the Camunda gateway, and the
  Connectors deployment. Reconcile-and-restart on change.

- **`camunda-security-library` (CSL)** — central authentication authority. Multi-OIDC plumbing
  already exists. Closes auth-layer gaps per phase (per-provider logout, per-provider user-login
  enable/disable, per-IdP claim mapping consistency, gRPC validation).

- **`camunda/camunda` (Orchestration Cluster)** — consumes CSL. Phase 1 changes are minimal
  because CSL does the heavy lifting. Authorization and mapping-rule features of the OC are
  reused as-is for Story 5 (external client authorizations) and the groups-claim path.

### Out-of-repo deliverables tracked here

- Auth0 tenant configuration for per-cluster `/post-logout` URLs (infra/Terraform).
- A feature ticket against `camunda/web-modeler` for Story 3 (graceful handling on BYOIDP
  clusters).
- A coordination ticket with PM + Documentation for help-link content and provider-specific
  setup guides (Ping, Microsoft Entra).

## Phasing

| Phase                                                              | Theme                                                                                                                                                                                 |
|--------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Phase 1**                                                        | BYOIDP user-facing MVP: customer can configure a custom IdP, log in via it, log out, and the Web Modeler knows BYOIDP is on. Auth0 remains active. Lockout safeguards in UX and docs. |
| **Phase 2**                                                        | Connectors authenticate to the cluster via the customer's IdP. External client authorization management in Console.                                                                   |
| **Phase 3**                                                        | Groups-claim productization. Camunda Support BYO-IdP onboarding. Auth0-disable toggle for user login. Closure of the PoC validation items.                                            |
| **Phase 4** *(may be split out as its own epic — see action item)* | Session idle timeout (cluster default + per-IdP override) with audit logging.                                                                                                         |

Each phase is intended to be shippable on its own.

---

## Phase 1 — BYOIDP user-facing MVP

**User stories addressed (in full or part):** Story 1 (intuitive setup), Story 6 (Camunda Support
read-only — implicit, via Auth0 staying alive; this implicit coverage holds only while Phase 3's
Auth0-disable toggle has not shipped — see Phase 3 for the customer-onboarded-support-identity
path that replaces it), Story 7 (documentation clarity).

### `camunda-cloud-management-apps` (Console)

**Frontend** (`packages/c4/src/components/c4-cluster-settings/elements/`)

- New element for an "External Identity Provider (OIDC)" toggle and form under
  *Cluster > Settings*, matching the pattern used by existing elements
  (Authorizations, Multi-Tenancy).
- When the toggle is on, the form collects: friendly display name, Issuer URL, Client ID,
  Client Secret, Scopes, and claim-mapping inputs (username-claim, clientid-claim, optional
  groups-claim).
- **Redirect URI** is not collected. The POC verified that Spring's default
  `{baseUrl}/sso-callback` derived from the actual request works. Customers register this URL
  in their IdP app registration as part of the documented setup. If real-world adoption needs
  configurable Redirect URIs, a follow-up adds the field — open question Q10.
- **Pre-save configuration validation** (OIDC discovery fetch, claim-presence checks,
  client-credentials test) is **explicitly out of scope for Phase 1**. The historic doc defers
  it; the AC's "real-time feedback on errors" is achieved through cluster startup failure
  surfacing instead. Productizing validation is a follow-on item — action item 12.
- The form **surfaces a lockout-risk warning before commit** (per the updated product brief).
  Final copy is an action item with Design.
- The friendly display name is also configurable for the existing Auth0 entry, so the login picker
  can show both providers with sensible labels. Today Auth0 has no friendly name in the picker.
- Visibility/edit gated to organization admins, matching the Authorizations toggle gating.
- **Number of custom IdPs per cluster in the Phase 1 UI is unresolved** (see action items). The
  POC, the CSL config layer, and the Operator CRD (array shape) all support multiple custom IdPs
  per cluster. Whether the Phase 1 UI exposes a single-form (simpler) or a list/CRUD shape from
  day one depends on Design effort and customer demand. The CRD and CSL stay multi-capable either
  way so the backend does not change when the UI expands.

**Backend** (`apps/console-backend/src/`)

- `Cluster` entity (TypeORM): non-secret IdP fields stored in a JSON column on the existing
  cluster record.
- A new route for IdP configuration — placement (new `IdpConfigRouter.ts` vs. extension of
  `ClusterRouter.ts`) to be confirmed with the Console team.
- A new controller for IdP secret management modeled on `connectorSecrets.controller.ts`.
  Secrets stored in AWS Secrets Manager / GCP Secret Manager under the key
  `idp-config-{clusterId}` (per cluster region). Secrets are masked on read and never returned to
  the frontend.
- `k8s-spec.utils.ts` extended so `generateSpec()` emits `Spec.Identity.OidcProviders[]` entries
  on the `ZeebeCluster` CR, with the client secret expressed as a `secretRef`.
- Pod restart on change reuses the existing reconfigure-and-restart flow used by other cluster
  settings toggles.
- New field on `GET /external/clusters` and `GET /external/clusters/:uuid` responses:
  `externalIdpEnabled: boolean`. Consumed by Web Modeler in its own repo.
- Audit log entries for changes to BYOIDP configuration, matching existing settings audit
  patterns.

### `camunda-operator` (Operator)

- **CRD change** in `api/cloud/v1alpha1/zeebecluster_types.go`: add
  `OidcProviders []OidcProviderSpec` to `IdentitySpec`. Each entry carries: `Name`, `IssuerURI`,
  `ClientID`, `ClientSecretRef` (Kubernetes `SecretKeySelector`), `Scopes []string`, `ClientName`,
  `UsernameClaim`, `GroupsClaim`, `ClientIDClaim`. Claim-mapping fields are optional.
- Regenerate CRD manifests, deepcopy, and golden test files
  (`make all-manifests && make update-golden-tests`).
- **Env var rendering** in `pkg/apps/camunda/camunda.go`: render each `OidcProviders[]` entry into
  the `CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_*` environment variables on the
  unified Camunda app. The client secret is wired via `secretKeyRef`.
- **Gateway configmap** (`pkg/apps/camunda/gateway/resources/config.go`): mirror the provider
  configuration into the gateway configmap so gRPC paths trust the same set of issuers. Update
  the gateway golden file.
- Pod restart on env-var change is handled by the existing reconcile-and-restart flow.

### `camunda-security-library` (CSL)

Phase 1 is mostly *already done*. Verification work, plus closing the gap noted in the POC and
adding observability:

- Confirm that providers configured under `camunda.security.authentication.providers.oidc.<name>.*`
  reach the Spring login picker with their `clientName` rendered.
- Verify the per-provider `idpLogoutEnabled` behavior. The POC flagged this as a gap (the flag
  was read from the top-level `camunda.security.authentication.oidc` config only); recent CSL
  work may have closed it. If it remains a gap, close it so customer IdP and Auth0 can choose
  RP-initiated logout independently. Tracked as open question Q2.
- **Observability**: emit per-provider metrics for login success, login failure (by reason
  bucket), token-rejection by issuer, and JWKS refresh failure. A customer-IdP misconfiguration
  otherwise silently lands customers on a failing cluster with no operator-visible signal.
- Add a CSL ADR documenting BYOIDP support.

### `camunda/camunda` (Orchestration Cluster)

- No new auth code in this repo for the primary login flow — CSL covers it.
- Extend the existing multi-provider integration tests in
  `authentication/src/test/java/io/camunda/authentication/MultipleOidcProviderFlowTest.java` (and
  related) to cover Auth0 + a customer-IdP shape end-to-end.
- **User-profile IdP indicator** (epic Details: *"Cluster should show … in user profile which
  IdP was used to login"*). Add the issuer / friendly name of the IdP that authenticated the
  current session to the user-profile pane in the OC webapp. Small frontend change in
  `webapp/client/`. Open question Q11 if Console renders this instead — see open questions.
- **Authentication-event audit scoping** (epic AC: *"Tokens, sessions, and audit events reflect
  correct scoping"*). Authentication and session events must carry which IdP issued the
  underlying token so audit consumers can attribute access. Scope of change depends on the
  current audit-event schema — Phase 1 adds the IdP attribution to login/logout/session-create
  audit records.

### Out of repo (Phase 1)

- **Auth0 tenant configuration**: register the cluster's `/post-logout` URL pattern in Auth0's
  Allowed Logout URLs. Today the URL is not registered, so logout via Auth0 lands on an Auth0
  error page (see historic doc). Tracked as an infra/SRE ticket.
- **Web Modeler ticket**: file a feature ticket in `camunda/web-modeler` to consume
  `externalIdpEnabled` on the cluster API responses and render the "deployment unsupported on
  external-IdP clusters" UX from Story 3. The ticket is a deliverable of this breakdown.
- **Documentation tickets**: a coordination ticket with Product and Documentation to write the
  BYOIDP setup walkthrough (with Ping and Microsoft Entra specifics), the support-ticket
  break-glass recovery procedure, and the user-facing security/access clarity content (Story 7).

---

## Phase 2 — Connectors via customer IdP + external client authorization

**User stories addressed:** Story 2 (Connectors via customer IdP), Story 5 (external client
authorization management).

### `camunda-cloud-management-apps` (Console)

**Frontend**

- New element under the BYOIDP card in cluster settings: a sub-toggle to enable "Connector
  authentication via my IdP," with inputs for Connectors-specific Client ID, Client Secret, and
  Audience. Distinct from the user-login OIDC client.
- A status indicator showing whether Connectors are currently authenticating successfully (data
  source mechanism is an open question — see action items).
- **External client authorization screen**: a new screen in Console that lets admins assign
  permissions to client IDs created in the customer's IdP. The UI is in Console; the underlying
  authorization model is the OC's existing client/mapping-rule infrastructure (no new OC screen).

**Backend**

- `Cluster` entity additions: `connectorIdpClientId`, `connectorIdpAudience` (non-secret),
  `connectorIdpEnabled` flag.
- New secret in AWS SM / GCP SM: `connector-idp-{clusterId}`.
- `k8s-spec.utils.ts` extended to emit the Connectors-OIDC fields onto the CR when the toggle is
  on.

**Out of scope for Phase 2:** changes to the `proxy-token-issuer` app for the customer-IdP path.
Customer-IdP tokens are obtained by the Connectors directly from the customer's IdP, so the
proxy is bypassed for those clusters. The proxy's permission-verification role needs separate
discussion — see action items.

### `camunda-operator` (Operator)

- New typed CRD field on `IdentitySpec` (or `ConnectorBridge` — to be decided in design):
  `ConnectorOidcClient *ConnectorOidcClientSpec` with `ClientID`, `ClientSecretRef`, `TokenURL`,
  `Audience`.
- In `pkg/apps/connectors/resources/connectors_deployment.go`: when this field is set, the
  Connectors deployment's `CAMUNDA_CLIENT_AUTH_TOKEN-URL` is overridden with the customer IdP's
  token URL, and `CAMUNDA_CLIENT_AUTH_CLIENT_ID`, `CAMUNDA_CLIENT_AUTH_CLIENT_SECRET` (via
  `secretKeyRef`), and `CAMUNDA_CLIENT_AUTH_AUDIENCE` are added. When unset, current behavior
  (proxy-token-issuer) is preserved.
- Tests and golden files for both branches.

### `camunda-security-library` (CSL)

- The CSL filter chain for **gRPC** is separate from REST. Phase 2 must include multi-provider
  gRPC validation, because both Story 2 (Connectors communicate via REST/gRPC) and Story 5 (the
  C8 REST/gRPC API) explicitly require it. Add gRPC-side tests for multi-provider token
  acceptance and authorization; fix any gaps surfaced.
- The OC already trusts the customer IdP from Phase 1 on the REST path; the gRPC path is the
  scope of this Phase 2 deliverable.

### `camunda/camunda` (Orchestration Cluster)

- No new authorization screen. The Console UI for Story 5 calls into existing OC APIs and reuses
  the current authorization model.

### Out of repo (Phase 2)

- Documentation: connector-cutover migration guide (the manual process the customer follows in
  their IdP to set up a Connectors client).

---

## Phase 3 — Groups-claim, Camunda Support BYO-IdP onboarding, Auth0-disable, PoC closure

**User stories addressed:** Story 4 (optional groups import), Story 6 (Camunda Support read-only,
second user story regarding customer-onboarded identities), and the remaining PoC validation
items from the product brief.

### `camunda-cloud-management-apps` (Console)

- **Groups-claim productization.** Phase 1 already provides the input. Phase 3 surfaces helper
  text and examples for common IdPs (Entra `groups`, Okta `groups`, Ping `memberOf`), inline
  validation that the claim is present in a test token, and links to OC mapping-rule docs so
  admins can map IdP group names to Camunda groups.
- **Auth0-disable toggle.** New element: "Disable Camunda SaaS login (Auth0) on this cluster."
  Visible only when at least one custom IdP is configured. Default off. Warning copy explains the
  recovery path (the support-ticket break-glass).
- **Camunda Support onboarding (provisional).** A docs-led deliverable: customers add a designated
  Camunda Support identity to their IdP and grant it read-only privileges. A Console status card
  indicating whether the Auth0 fallback is active and/or a customer-onboarded support identity is
  configured is a candidate ride-along. The precise scope depends on resolution of the
  Camunda Support onboarding process action item.

### `camunda-operator` (Operator)

- Add a `UserLoginEnabled *bool` field on the existing `OidcProviderSpec` entry (from Phase 1).
  The Console-side Auth0-disable toggle writes `userLoginEnabled=false` on the Auth0 provider
  entry, rather than a separate cluster-wide `DisableAuth0UserLogin` field. This keeps a single
  per-provider data model and avoids a duplicate, Auth0-specific shape. Operator renders the
  field into the corresponding CSL env var.
- Regenerate manifests, deepcopy, golden tests.

### `camunda-security-library` (CSL)

- **Per-provider user-login enable/disable.** Today, providers are both trusted for token
  validation AND offered in the login picker. Phase 3 introduces a per-provider
  `userLoginEnabled` flag. The flag's exact semantic (does it gate only login-picker visibility,
  or also issuer trust for M2M tokens?) is the topic of action item 10. CSL ships the flag; the
  resolution of that action item determines whether the flag affects M2M trust as well.
- **PoC closure work** (validation activities, not features):
  1. Logout RP-initiated: ensure per-provider `idpLogoutEnabled` interoperates correctly across
     providers; logging out of one does not prematurely end the other's session.
  2. Multi-IdP claim mapping: integration tests that groups/username/clientid claims resolve
     consistently to Camunda's identity model regardless of issuer. Groups is the highest-risk
     seam.
  3. AuthZ enforcement across IdPs: tests asserting identical authorization decisions for
     equivalent resolved identity, independent of issuer.
- A CSL ADR documenting the user-login-vs-M2M split.

### `camunda/camunda` (Orchestration Cluster)

- Consumer-side tests for the Auth0-disabled-for-login + Auth0-trusted-for-M2M configuration once
  CSL ships the split.

### Out of repo (Phase 3)

- Documentation: Auth0-disable operational guide, BYO IdP support-identity onboarding procedure,
  reinforced break-glass recovery instructions.

---

## Phase 4 — Session idle timeout + audit logging *(may be split out as a separate epic)*

**Status:** the product brief marks this as a stretch. It is included here for completeness but
its inclusion in this epic is an open question.

### `camunda-cloud-management-apps` (Console)

- New element under the BYOIDP card for session idle timeout: a cluster-level default plus per-IdP
  overrides. Range 5 minutes – 24 hours. Range validation client- and server-side.
- Audit log entries on every change to either the cluster default or any per-IdP override.

### `camunda-operator` (Operator)

- New CRD fields: `Spec.Identity.SessionIdleTimeoutSeconds` (cluster default) and
  `SessionIdleTimeoutSeconds` on each `OidcProviderSpec` entry.
- Render the corresponding env vars consumed by CSL.

### `camunda-security-library` (CSL)

- Per-provider idle timeout resolution: cluster default property plus
  `camunda.security.authentication.providers.oidc.<name>.session-idle-timeout` override. At session
  creation, resolve based on which provider authenticated the user. Enforce the 5 min – 24 h
  bounds. CSL ADR for the resolution rule and bounds.

### `camunda/camunda` (Orchestration Cluster)

- Audit-log entries for changes to the new fields flow through the existing settings audit
  pipeline.

---

## Out of scope (across all phases)

- Organization-wide IdP configuration (per the epic; org-level remains Auth0-only until Camunda
  Hub delivers a unified identity stack).
- Replacing Auth0 entirely (Approach B from the POC).
- Dynamic OIDC reconfiguration without restart (Approach C from the POC). Pod restart on config
  change is acceptable.
- Per-tenant or Physical Tenant identity binding (called out as out of scope in the product
  brief).
- Web Modeler internal implementation. Only the Console-side `externalIdpEnabled` signal and the
  WM feature ticket are tracked here.

---

## Action items

Each item below needs an owner and a resolution; many gate scope refinement in their phase.

### Cross-cutting

1. **File the Web Modeler feature ticket** in `camunda/web-modeler` for Story 3, referencing the
   Console-emitted `externalIdpEnabled` signal. Owner: PM + breakdown author.
2. **Auth0 tenant configuration ticket**: register the per-cluster `/post-logout` URL pattern in
   Auth0's Allowed Logout URLs. Owner: infra/SRE.
3. **PM + Documentation coordination ticket**: help-link content, provider-specific setup guides
   (Ping, Microsoft Entra including the `preferred_username` + `preferUsernameClaim` pattern), the
   support-ticket break-glass recovery procedure, and Story 7's security/access clarity content.
   Owner: PM + Docs.

### Phase 1

4. **Final copy text from Design** for the Console FE BYOIDP element (title, description, link
   text). Owner: Product Design.
5. **Confirm route placement with the Console team** — new `IdpConfigRouter.ts` vs. extension of
   `ClusterRouter.ts`. Owner: Console engineering.
6. **Lockout-warning UX copy** finalized for the pre-commit warning. Owner: Product Design + PM.
7. **Decide whether the Phase 1 UI exposes one or many custom IdPs per cluster.** The POC, CSL,
   and Operator CRD all support multiple custom IdPs; the question is whether the Console UI
   ships a single-form shape or a list/CRUD shape in Phase 1. Heavier FE work for the latter.
   Owner: Product Design + Console FE engineering.
12. **Pre-save configuration validation** (OIDC discovery fetch, claim-presence checks,
    `client_credentials` test) — confirm Phase 1 deferral with PM, and either file a follow-on
    ticket for productized validation or document the indefinite deferral. Owner: PM.
13. **Security review.** This is a security-critical feature (two trusted issuers, SaaS
    validators tolerating missing org/clusterId claims). Schedule an explicit security review of
    the Phase 1 design before code lands. Owner: Security team + breakdown author.
14. **Cluster generation / minimum-version gate.** The sibling multi-tenancy epic gated the UI
    on Zeebe 8.8.0-alpha7+. Decide which cluster generations expose the BYOIDP toggle, and gate
    the Console UI accordingly. Owner: PM + Console engineering.
15. **Feature rollout.** Decide whether BYOIDP is exposed behind a Console feature flag, and what
    the rollout plan looks like. Owner: PM.

### Phase 2

8. **Discuss `proxy-token-issuer` rearchitecture.** The proxy's permission-verification job is
   independent of token issuance and does not apply to customer-IdP clients (those tokens are
   issued by the customer's IdP using the customer's credentials; Camunda cannot substitute or
   cache them). Two design options to evaluate: extract a validation middleware that any service
   can call before issuing/accepting tokens, or expose a standalone introspection endpoint
   downstream services consume directly. Decision is out of scope for this breakdown; resolution
   shapes Phase 2 design. Owner: Console / `proxy-token-issuer` engineering.

### Phase 3

9. **Clarify the Camunda Support BYO-IdP onboarding process.** Concrete mechanism: well-known
   identity name, who creates it (customer or Camunda), lifecycle, revocation, audit trail. Phase
   3's Camunda Support scope refines after this is resolved. Owner: PM + Security + Support.
10. **Discuss whether the Auth0-disable toggle should keep M2M access working.** Splitting
    user-login trust from M2M trust per provider is substantial CSL work; a simpler
    all-or-nothing semantic ("disable Auth0 = no Auth0 trust at all") may be preferable, even if
    that means re-evaluating Controller/Web Modeler/Connectors paths. Phase 3 CSL and Operator
    scope is provisional until this is resolved. Owner: PM + CSL engineering.

### Phase 4

11. **Decide whether session idle timeout is a separate epic.** If kept here, clarify the
    cluster-default vs. per-IdP-override semantics, finalize the 5 min – 24 h bounds, and define
    the audit-event shape. Owner: PM.

---

## Open questions

These do not block scoping but need answers during execution.

### Phase 1

- **Q1.** Does the OC need to expose an `externalIdpEnabled` signal at all? Console likely derives
  this from its own database; an OC-side surface is only needed if a non-Console consumer also
  needs it. Working assumption: **no OC change**.
- **Q2.** Is the per-provider `idpLogoutEnabled` flag still a gap in CSL, or has recent CSL work
  closed it? Verify against current CSL state when execution begins.
- **Q3.** Does Console already propagate a `CLIENTNAME` for the existing Auth0 entry, or is
  passing a friendly Auth0 name through a new propagation?
- **Q10.** Redirect URI: is the `{baseUrl}/sso-callback` default sufficient for all customer IdPs,
  or do some require a different callback path? If real-world adoption exposes a need, a
  Redirect-URI field is added in a follow-on.
- **Q12.** JWKS / discovery resilience: what is the expected behavior when the customer IdP's
  discovery endpoint is unreachable at pod start, or JWKS is unreachable mid-session? Define
  timeouts, retry policy, and whether transient customer-IdP failures must keep Auth0 (and the
  rest of the cluster) usable.
- **Q13.** Identity reconciliation on enablement: when BYOIDP is turned on, the same human user's
  `sub` claim from the customer IdP differs from their Auth0 `sub`. Per-user authorizations
  bound to Auth0 `sub` are orphaned. Groups-claim helps for group-scoped permissions but not for
  per-user grants. Document the expected admin workflow, or define a migration deliverable.
- **Q15.** Login picker: is the Spring Security default login picker (one button per configured
  provider) sufficient for general availability, or does the product require a Camunda-designed
  login page? A custom page gives control over branding, copy, and provider order but is a
  non-trivial frontend investment. Resolve before Phase 1 design.

### Phase 2

- **Q4.** What does "access tokens are cached and refreshed in accordance with the IdP's token
  lifetime and refresh token policies" mean concretely with the direct-to-IdP Connectors flow?
  The Camunda Spring client handles caching/refresh by default; the AC may already be satisfied,
  or it may require additional UX/observability.
- **Q5.** What mechanism does Console use to detect Connectors-side authentication failures so it
  can render the "Connector cannot authenticate to cluster" error from the AC? A healthcheck
  signal, a deployment status, or something else?
- **Q14.** Existing-flows breaking-change risk on BYOIDP-enabled clusters. On a cluster with
  BYOIDP enabled where Auth0 is still trusted, hybrid clients currently routing through
  `proxy-token-issuer` (and any other indirect Auth0-token flow) must be characterized:
  do they continue to function unchanged, are they explicitly out of scope, or do they need a
  migration path? Resolve during Phase 2 design — closely related to action item 8.

### PoC validation items (gate on closure before claiming the affected phases done)

- **Q6.** Logout — RP-initiated logout where supported; verify cross-provider behavior with at
  least one customer IdP and Auth0 in coexistence.
- **Q7.** Multi-IdP claim mapping — groups/username/clientid resolve consistently across IdPs.
  Groups is the highest-risk seam.
- **Q8.** AuthZ enforcement across IdPs — same authorization decision regardless of issuer for
  equivalent resolved identity.
- **Q9.** gRPC auth — separate filter chain from REST; needs its own multi-provider validation.
  Possibly the heaviest PoC item.

---

## References

- Historic POC notes and approach evaluation: [
  `docs/evaluate-byoidp-saas.md`](./evaluate-byoidp-saas.md)
- Multi-OIDC commit in this repo (now consumed via CSL): `bfe2316a` (
  `feat: support multiple OIDC providers`)
- Example sibling epic (multi-tenancy in cluster settings — same propagation pattern):
  [camunda/product-hub#3244](https://github.com/camunda/product-hub/issues/3244), with operator
  change [camunda/camunda#53629](https://github.com/camunda/camunda/issues/53629) and Console
  change [camunda/camunda#53519](https://github.com/camunda/camunda/issues/53519)
