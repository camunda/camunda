# BYOIDP Epic — Handover Notes for Follow-Up Agents

This document is a technical companion to
[`docs/byoidp-saas-breakdown.md`](./byoidp-saas-breakdown.md). It is written for AI agents (and
new engineers) picking up component-level specs or implementation in any of the four affected
repos. It does **not** restate decisions or scope — read the breakdown for those. Treat this file
as a navigation aid: verified file paths, env-var conventions, sibling-epic ticket templates, and
relevant POC findings, gathered once so future sessions don't have to re-explore.

> **Source of truth:** the breakdown document and its Action Items / Open Questions section.
> If anything in this handover conflicts with the breakdown, the breakdown wins.

## Branch / repo state

- This repo (`camunda/camunda`): branch `evaluate-byoidp-saas`, rebased on `main`.
- Doc commits on the branch:
  - `a38c92bf352` — historic POC notes ([`docs/evaluate-byoidp-saas.md`](./evaluate-byoidp-saas.md))
  - `e695737488d` — initial breakdown
  - `d0610a09214` — review-driven breakdown updates
- Sibling repos used during exploration (assumed at the same parent directory as `camunda/`):
  `camunda-cloud-management-apps`, `camunda-operator`, `camunda-security-library`. None of them
  has a BYOIDP branch yet — Phase 1 work will create branches in each.

## Cross-cutting reference

### OIDC env-var family

The Operator emits this family of env vars on the unified Camunda app (and mirrors them into the
gateway configmap). CSL reads them via Spring relaxed binding into the providers map.

```
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_ISSUERURI
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_CLIENTID
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_CLIENTSECRET
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_CLIENTNAME
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_SCOPES
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_USERNAMECLAIM
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_GROUPSCLAIM
CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_<NAME>_CLIENTIDCLAIM
```

`<NAME>` is a Spring registration id — e.g., `AUTH0`, `ENTRA`, `OKTA`. Phase 3 adds a per-provider
`USERLOGINENABLED` (boolean). Phase 4, if it stays in this epic, adds
`SESSIONIDLETIMEOUT` (seconds). The Phase 1 default Redirect URI is `{baseUrl}/sso-callback` —
no env var; Spring derives it from the request. POC validated on `console.dev.ultrawombat.com`.

The Connectors deployment uses a different env-var family for its outgoing client auth (note
the dash in `TOKEN-URL` — Spring relaxed binding handles it):

```
CAMUNDA_CLIENT_AUTH_TOKEN-URL
CAMUNDA_CLIENT_AUTH_AUDIENCE
CAMUNDA_CLIENT_AUTH_CLIENT_ID     # Phase 3, when customer-IdP Connector auth is enabled
CAMUNDA_CLIENT_AUTH_CLIENT_SECRET # Phase 3, via secretKeyRef
```

### Spring multi-OIDC behaviors that affect acceptance criteria interpretation

- Spring's default multi-provider login picker renders all configured providers automatically,
  using each provider's `clientName`. No new login-picker page needed for Phase 1.
- SaaS validators (`OrganizationValidator`, `ClusterValidator`) tolerate missing Camunda-specific
  claims (`https://camunda.com/orgs`, `https://camunda.com/clusterId`). This is what makes the
  dual-provider model work for customer IdPs that don't carry Camunda claims — but it also means
  there is no organization/cluster scoping check on customer-IdP tokens beyond issuer/audience.
- The current `idpLogoutEnabled` field in CSL is a **single top-level** flag at
  `api/src/main/java/io/camunda/security/api/model/config/oidc/OidcConfiguration.java`. Phase 1
  open question Q2: verify whether per-provider semantics have already landed; close the gap if
  not.

### Sibling-epic ticket templates (use as structure for Phase 1 tickets)

The multi-tenancy epic (camunda/product-hub#3244) used the same propagation pattern this
breakdown extends. The two PR-spec-shaped tickets are excellent templates:

- **Console-side template:** [camunda/camunda#53519](https://github.com/camunda/camunda/issues/53519)
  — settings element, env-var generation, `*Enabled` field on `GET /external/clusters`,
  "where to implement" + AC + out-of-scope sections.
- **Operator-side template:** [camunda/camunda#53629](https://github.com/camunda/camunda/issues/53629)
  — CRD field, env propagation, golden-test regeneration, scope statement.

Phase 1 Console ticket parallels #53519; Phase 1 Operator ticket parallels #53629.

### CSL conditional annotations (when wiring new beans)

CSL ships these meta-annotations in `io.camunda.security.spring.annotation`:

- `@ConditionalOnAuthenticationMethod(OIDC|BASIC)`
- `@ConditionalOnProtectedApi` / `@ConditionalOnUnprotectedApi`
- `@ConditionalOnInternalUserManagement`
- `@ConditionalOnCamundaGroupsEnabled`

Prefer these over raw `@ConditionalOnProperty` for security-related gating.

### POC findings to carry into design

- **Auth0 logout** currently lands on
  `https://weblogin.cloud.dev.ultrawombat.com/u/login/identifier` with an "Oops!" error. Root
  cause: the cluster `/post-logout` URL is not in Auth0's `Allowed Logout URLs`. Fix is in
  Auth0/Terraform, tracked outside the four repos (action item 2 in the breakdown).
- **Entra logout** was verified working with `post_logout_redirect_uri={baseUrl}/post-logout`.
- POC env vars used during validation (template for Operator output):
  ```
  CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_ISSUERURI=https://login.microsoftonline.com/<tenant>/v2.0
  CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_CLIENTID=<client-id>
  CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_CLIENTSECRET=<secret>
  CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_ENTRA_CLIENTNAME=EntraSSO
  ```

## Per-repo orientation

### `camunda-cloud-management-apps` (Console)

Largest share of work; Console owns the UI, secret storage, and the `ZeebeCluster` CR writes.

**Frontend (Phase 1 entry points):**
- Settings tab: `packages/c4/src/widgets/cluster-details/tabs/c4-cluster-details-settings-tab.tsx`
- Settings container: `packages/c4/src/components/c4-cluster-settings/c4-cluster-settings.tsx`
- Per-setting element files to mirror:
  `packages/c4/src/components/c4-cluster-settings/elements/multi-tenancy.tsx`,
  `swagger-toggle.tsx`, `resource-based-authorizations.tsx`, `mcp-toggle.tsx`,
  `low-latency-toggle.tsx`. New file for Phase 1: `external-identity-provider.tsx` (or similar).
- Storybook and spec patterns are co-located with each element.

**Backend (Phase 1 entry points):**
- Router: `apps/console-backend/src/routes/ClusterRouter.ts` (`PATCH /:clusterId` handles
  settings changes today). Open question: whether IdP gets its own router (`IdpConfigRouter.ts`)
  or extends this one — action item 5.
- Cluster entity: `apps/console-backend/src/entities/Cluster.ts` (TypeORM). Non-secret fields go
  in a JSON column on the existing record.
- Secret pattern template (copy this): `apps/console-backend/src/controller/connectorSecrets.controller.ts`.
  Connector secrets are stored in **AWS Secrets Manager and GCP Secret Manager** (dual-provider
  based on cluster region) under the key `connector-secrets-{clusterId}`. Secrets are masked as
  `"XXX"` when listed and never returned to the frontend. The IdP equivalent should be named
  `idp-config-{clusterId}`.
- K8s spec generation: `apps/console-backend/src/controller/k8s/k8s-spec.utils.ts`. The
  `generateSpec()` function builds the `ZeebeCluster` CR (including `overrideEnv` arrays the
  Operator consumes).
- `apiClient.controller.spec.ts` / `zeebeClient.controller.spec.ts` already validate
  `clientCredentials` for cluster API clients — relevant prior art for Story 5 (external client
  authorization management).

**Proxy-token-issuer** (Phase 3 rearchitecture topic — action item 8):
- Located at `apps/token-issuer/` in the Console repo.
- Auth0 token controller: `apps/token-issuer/src/auth0/auth0.token.controller.ts` — uses
  `grant_type: client_credentials` against Auth0.
- Operator config that points Connectors at the proxy: `CONNECTORS_TOKEN_ISSUER_URL` env var
  (defaults to `http://proxy-token-issuer.proxy-token-issuer.svc.cluster.local/oauth/token` in
  `pkg/config/config.go`).

### `camunda-operator` (Operator)

**CRD owner; pattern is typed fields, no generic `overrideEnv` on `IdentitySpec`.**

- CRD types: `api/cloud/v1alpha1/zeebecluster_types.go`. `IdentitySpec` currently has
  `ResourcePermissions`, `MultiTenancy`, `MultiTenancyApiEnabled`, `ResourceAuthorizations`.
  Phase 1 adds `OidcProviders []OidcProviderSpec`. Phase 3 adds both `ConnectorOidcClient
  *ConnectorOidcClientSpec` and a `UserLoginEnabled *bool` field on each `OidcProviderSpec`
  entry (not a separate cluster-wide bool).
- App env-var rendering: `pkg/apps/camunda/camunda.go` (`IdentityTogglesEnvVars` is the
  reference function for how identity-related env vars are built).
- Gateway configmap (separate from the app's env): `pkg/apps/camunda/gateway/resources/config.go`
  + `templates/camunda_gateway_configmap.yaml`. Has its own multi-tenancy config; OIDC providers
  need similar mirroring so gRPC paths trust the same set of issuers.
- Connectors deployment: `pkg/apps/connectors/resources/connectors_deployment.go`.
  `setCamundaClientEnvVars` (~line 198) sets `CAMUNDA_CLIENT_AUTH_TOKEN-URL` and
  `CAMUNDA_CLIENT_AUTH_AUDIENCE` today. Phase 3 adds `CAMUNDA_CLIENT_AUTH_CLIENT_ID` and
  `CAMUNDA_CLIENT_AUTH_CLIENT_SECRET` when the customer-IdP Connector flag is set.
- After CRD/template changes, regenerate:
  `make all-manifests && make update-golden-tests && make test`. Golden files are extensive — any
  env-var change touches multiple `*_test.go` golden outputs.

### `camunda-security-library` (CSL)

**Central authentication authority. New auth code lands here, not in this repo's
`authentication/`.**

- Maven coordinates already declared in `camunda/camunda`:
  - `configuration/pom.xml` uses `camunda-security-library-spring-boot-starter` and
    `camunda-security-library-api`.
  - `dist/pom.xml` uses `camunda-security-library-{core,api,spring-boot-starter,validation}` and
    related security artifacts.
- Key OIDC source files (in `spring-boot-starter/src/main/java/io/camunda/security/spring/oidc/`):
  - `OidcAuthenticationConfigurationRepository.java` — merges the legacy `oidc.*` and
    `providers.oidc.<name>.*` configs into one map.
  - `OidcBeansConfiguration.java` — Spring wiring.
  - `IssuerAwareJWSKeySelector.java` and `IssuerAwareTokenValidator.java` — per-issuer routing.
  - `TokenValidatorFactory.java` — per-registration validator instantiation.
  - `ScopedClientRegistrationFactory.java`, `ScopedJwtDecoderFactory.java`,
    `ScopedOidcInfrastructureConfiguration.java` — per-scope (REST vs gRPC vs API vs Webapp)
    customization.
  - `CachingOidcClaimsProvider.java` — UserInfo augmentation with caching. M2M / client-credentials
    detection lives here.
- Security filter chains (in `spring-boot-starter/src/main/java/io/camunda/security/spring/security/`):
  `OidcWebappSecurityConfiguration.java`, `OidcApiSecurityConfiguration.java`,
  `CamundaOidcLogoutSuccessHandler.java`, `LoginLinksBuilder.java`.
- `OidcConfiguration.idpLogoutEnabled` lives in
  `api/src/main/java/io/camunda/security/api/model/config/oidc/OidcConfiguration.java` as a
  single top-level field — Phase 1 verifies whether per-provider semantics have already landed
  (Q2).
- If a CSL ADR is needed in later phases, the location is `docs/adr/` *in the CSL repo* (not in `camunda/camunda/docs/adr/`).
- Test prior art:
  - `OidcWebappLoginPickerTest.java`
  - `OidcWebappMultiIdpRedirectLoopTest.java`
  - `OidcBeansConfigurationMultiIssuerDecodeTest.java`
  - `OidcBeansConfigurationCompositeJwtDecodeTest.java`
  - `CamundaOidcLogoutSuccessHandlerTest.java`

### `camunda/camunda` (Orchestration Cluster — this repo)

Phase 1 has no new OC code — CSL does the heavy lifting. Useful landmarks for later phases:

- Existing integration tests in `authentication/src/test/java/io/camunda/authentication/`
  (`MultipleOidcProviderFlowTest.java`, `OidcFlowTest.java`,
  `OidcClientSecretBasicKeycloakTest.java`, `OidcPrivateKeyJwtKeycloakTest.java`,
  `SessionAuthenticationRefreshTest.java`) already cover the dual-provider path. Keycloak test
  realm fixtures: `authentication/src/test/resources/camunda-foo-realm.json`,
  `camunda-bar-realm.json`.
- `authentication/` already imports CSL classes (`io.camunda.security.spring.oidc.*`) — confirms
  CSL is the right home for new auth code.
- User-profile IdP indicator (Phase 4 stretch, open question Q11): location is the OC webapp at
  `webapp/client/` — specific path TBD during Phase 4 design.
- Doc home: `docs/` in this repo holds both
  [`evaluate-byoidp-saas.md`](./evaluate-byoidp-saas.md) (historic POC) and
  [`byoidp-saas-breakdown.md`](./byoidp-saas-breakdown.md) (this plan).

## Open-question quick lookup

When you encounter an unresolved decision, the breakdown's Open Questions section (`Q1`–`Q16`)
and Action Items section (1–15) are authoritative. The breakdown intentionally uses
non-sequential numbering to preserve traceability across edits — do not renumber when adding new
items in follow-up work.
