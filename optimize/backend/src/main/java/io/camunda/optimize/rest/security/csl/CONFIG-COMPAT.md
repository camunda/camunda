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

## Always set when CSL is enabled
| CSL property | Value |
|---|---|
| `camunda.security.authentication.method` | `oidc` |
| `camunda.security.unhandled-paths-chain.enabled` | `false` (Optimize webapp chain is `/**`) |

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
| Legacy | CSL property | Kind |
|---|---|---|
| `security.auth.cloud.clientId` / `clientSecret` / `customDomain` | Auth0 `...oidc.*` registration (client-id/secret, issuer-uri) | MAP (TODO) |
| `security.auth.cloud.audience` | `...oidc.audiences` | MAP (TODO) |
| `security.auth.cloud.userIdAttributeName` | `...oidc.username-claim` | MAP (TODO) |
| `security.auth.cloud.organizationId` | `...oidc.organization-id` | MAP (TODO) |
| `m2mClient.*`, `users.cloud.accountsUrl`, `organizationClaimName` | none | SAAS/NO-ANALOG |

## Transport (not part of the auth bridge)
`container.ports.*`, `container.keystore.*`, `container.enableSniCheck`, `container.contextPath`,
`container.http2Enabled` map to Spring/server settings (`server.port`, `server.ssl.*`,
`server.servlet.context-path`, ...) and are unchanged by CSL adoption.

## Mode selection
Optimize selects CCSM vs CCSaaS by Spring active profile (`ccsm` / `cloud`), defaulting to CCSM.
Both are OIDC under CSL; the bridge sets `method=oidc` for either. The Auth0 vs Identity difference
is which OIDC registration values are populated.
