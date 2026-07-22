# Config backward-compatibility mapping (ADR-0036)

How Optimize's existing auth/security config maps to CSL `camunda.security.*`, so operators are not
forced to migrate. Implemented by `OptimizeSecurityConfigCompatibilityPostProcessor`
(EnvironmentPostProcessor, low precedence, explicit `camunda.security.*` always wins), active only
when `optimize.security.csl.enabled=true`.

Every recognized legacy key logs a deprecation warning on use that names its `camunda.security.*`
replacement (or says it has no effect, for obsolete keys). The legacy keys stay supported until
**8.11** and are removed afterwards.

Legend: MAP = direct/semantic map applied by the bridge; OBSOLETE = no meaning under server-side
sessions/CSL, ignored with a deprecation warning; SAAS/NO-ANALOG = platform-managed, no CSL target;
TRANSPORT = Spring/server config, not part of the auth bridge (unchanged).

## Always set when CSL is enabled (defaults; explicit config still wins)
| CSL property | Value |
|---|---|
| `camunda.security.authentication.method` | `oidc` |
| `camunda.security.unhandled-paths-chain.enabled` | `false` (Optimize webapp chain is `/**`) |
| `camunda.security.authentication.oidc.redirect-uri` | `{baseUrl}/api/authentication/callback` (Optimize's existing callback; CSL derives its listener path from this, ADR-0036, so the pre-provisioned IdP client needs no change) |

## OIDC / CCSM (Camunda Identity)
| Legacy (env var / key) | CSL property | Kind |
|---|---|---|
| `CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL` (`security.auth.ccsm.issuerUrl`, `camunda.identity.issuer`) | `camunda.security.authentication.oidc.issuer-uri` | MAP |
| `CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID` | `...oidc.client-id` | MAP |
| `CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET` | `...oidc.client-secret` | MAP |
| `CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE` | `...oidc.audiences` | MAP |
| `CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL` (`ccsm.issuerBackendUrl`) | `...oidc.jwk-set-uri` / `token-uri` / `user-info-uri` (back-channel) | MAP (semantic; TODO) |
| `security.auth.ccsm.redirectRootUrl` | `...oidc.redirect-uri` (CSL redirect is `/sso-callback`) | MAP (semantic; TODO) |
| `security.auth.ccsm.diagnosticsEnabled` | `...oidc.diagnostics.*` | MAP (TODO) |
| `security.auth.ccsm.entraTokenVersionCheckEnabled` | handled by CSL (ADR-0033) | MAP if a toggle exists (TODO) |
| `security.auth.ccsm.baseUrl` | (Identity base URL; no direct chain analog) | NO-ANALOG |

## Public API JWT
| Legacy | CSL property | Kind |
|---|---|---|
| `api.jwtSetUri` (`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`) | `...oidc.jwk-set-uri` | MAP |
| `api.audience` (`CAMUNDA_OPTIMIZE_API_AUDIENCE`) | `...oidc.audiences` | MAP |
| `api.jwtAuthForApiEnabled` | behavioral: put the bearer-accepting `/api/**` paths in `apiPaths()` | SEMANTIC (path config, TODO) |
| `api.accessToken` (`OPTIMIZE_API_ACCESS_TOKEN`) | none (CSL API chain is bearer-JWT only) | OBSOLETE |

## Session / token / cookie
| Legacy | CSL property | Kind |
|---|---|---|
| `security.auth.token.lifeMin` | session timeout (`server.servlet.session.timeout` / CSL session config) | MAP (semantic: minutes -> duration; TODO) |
| `security.auth.token.secret` | none (self-signed JWT no longer minted) | OBSOLETE |
| `security.auth.cookie.maxSize` | none (cookie splitting gone) | OBSOLETE |
| `security.auth.cookie.same-site.enabled` | CSL sets SameSite on its session cookie | OBSOLETE (handled by CSL) |
| `security.auth.cookie.secure` | CSL cookie secure handling | OBSOLETE (handled by CSL) |

## Response headers
| Legacy | CSL property | Kind |
|---|---|---|
| `security.responseHeaders.HSTS.max-age` | `camunda.security.http-headers.hsts.max-age-in-seconds` (negative -> `hsts.disabled=true`) | MAP |
| `security.responseHeaders.HSTS.includeSubDomains` | `...http-headers.hsts.include-sub-domains` | MAP (TODO) |
| `security.responseHeaders.Content-Security-Policy` | `...http-headers.content-security-policy` | MAP (TODO) |
| `security.responseHeaders.X-Content-Type-Options` (bool) | `...http-headers.content-type-options.disabled` (inverted) | MAP (semantic; TODO) |
| `security.responseHeaders.X-XSS-Protection` | none (CSL does not emit this deprecated header) | OBSOLETE |

## CCSaaS / Auth0 (SaaS)

CSL owns SaaS org/cluster config via `camunda.security.saas.*` (requires BOTH `organization-id` and
`cluster-id`) plus `...oidc.organization-id`. It does NOT ship the org/cluster claim checks: those
are host-supplied `OAuth2TokenValidator<Jwt>` beans plugged in by overriding CSL's
`TokenValidatorFactory` bean (CSL ships only timestamp/issuer/audience validators). The cluster
sub-path (`/<clusterId>`) is handled by running Optimize under
`server.servlet.context-path=/<clusterId>` (Optimize's `container.contextPath`), so CSL's generated
login/callback/post-logout URLs and its matchers carry the prefix natively — this replaces the
legacy `CCSaasRequestAdjustmentFilter` clusterId stripping + `AddClusterIdSubPathToRedirect...`
entry point. The Optimize-specific `/external/api` -> `/api/external` rewrite and static-share
serving remain host concerns (a small filter), unrelated to clusterId.

The bridge detects cloud by the presence of `CAMUNDA_OPTIMIZE_AUTH0_CLIENTID` and maps these
`${...}` env vars (from `service-config.yaml`, `security.auth.cloud`):

| Legacy env var (`auth.cloud.*` field) | CSL target | Kind |
|---|---|---|
| `CAMUNDA_OPTIMIZE_AUTH0_CLIENTID` (`clientId`) | `...oidc.client-id` | MAP (bridged) |
| `CAMUNDA_OPTIMIZE_AUTH0_CLIENTSECRET` (`clientSecret`) | `...oidc.client-secret` | MAP (bridged) |
| `CAMUNDA_OPTIMIZE_AUTH0_DOMAIN` (`customDomain`) | `...oidc.issuer-uri` = `https://<domain>/` (CSL discovers token/jwks/userinfo) | MAP (bridged) |
| `CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE` (`audience`) | `...oidc.audiences` | MAP (bridged) |
| `CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION` (`organizationId`) | `...oidc.organization-id` + `camunda.security.saas.organization-id` | MAP (bridged) |
| `CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID` (`clusterId`) | `camunda.security.saas.cluster-id` (+ `server.servlet.context-path=/<clusterId>`, see clusterId note) | MAP (bridged) |
| (cloud login callback) | `...oidc.redirect-uri` = `{baseUrl}/sso-callback` (overrides the CCSM default) | MAP (bridged) |
| `userIdAttributeName` (`sub`) | `...oidc.username-claim` (CSL default `sub`) | MAP (no-op) |
| `CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_AUTH0_AUDIENCE` (`userAccessTokenAudience`) | webapp access-token audience; not separately enforced (CSL single-audience per registration) | TODO |
| `CAMUNDA_OPTIMIZE_AUTH0_TOKEN_URL` (`tokenUrl`) | none needed (discovered from `issuer-uri`) | NO-ANALOG |
| org-membership gate (`hasAccess`) + allowed org roles | `OptimizeCloudOidcUserService` (CSL `OidcUserService` webapp hook) | HOST-VALIDATOR |
| `https://camunda.com/clusterId` claim (public-API bearer) | `TokenValidatorFactory` override adds `CustomClaimValidator` (`OptimizeCloudSecurityConfiguration`) | HOST-VALIDATOR |
| `https://camunda.com/originalUserId` claim -> user-id migration | `OptimizeCslLoginSuccessListener` (host, on Spring `InteractiveAuthenticationSuccessEvent`) | HOST-HOOK |
| `m2mClient.*`, `users.cloud.accountsUrl` | none (cloud-console M2M clients, independent of the login chain) | SAAS/NO-ANALOG |

## Transport (not part of the auth bridge)
`container.ports.*`, `container.keystore.*`, `container.enableSniCheck`, `container.contextPath`,
`container.http2Enabled` map to Spring/server settings (`server.port`, `server.ssl.*`,
`server.servlet.context-path`, ...) and are unchanged by CSL adoption.

## Mode selection
Optimize selects CCSM vs CCSaaS by Spring active profile (`ccsm` / `cloud`), defaulting to CCSM.
Both are OIDC under CSL; the bridge sets `method=oidc` for either. The Auth0 vs Identity difference
is which OIDC registration values are populated.
