# Optimize Servlet Filters & Request Interceptors

This document describes every servlet filter and request interceptor registered in the Optimize
backend, their execution order, the deployment mode they apply to, and the reason they exist.

---

## Deployment Modes

|  Symbol  |                                                                                            Meaning                                                                                             |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **CCSM** | Self-Managed (on-premise). Tomcat `server.servlet.context-path` (e.g. `/optimize`) provides the path prefix natively.                                                                          |
| **SaaS** | Camunda Cloud. No context-path. Instead, a `clusterId` segment (e.g. `/abc-123/…`) is the first URI segment and must be stripped before the request reaches Spring Security or any controller. |

Both modes can run simultaneously — they are mutually exclusive, selected by `CCSMCondition` /
`CCSaaSCondition` at startup.

---

## Execution Order

Filters are executed in this order for every HTTP request. Where two filters share the same
effective order value, Spring Boot resolves the tie by bean registration order (alphabetically by
bean name unless an explicit name is set).

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│  Order              Filter / Component                  Mode       Registered in         │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│  Integer.MIN_VALUE  CCSaasRequestAdjustmentFilter       SaaS only  CCSaaSSecurityConfig  │
│  (default)          ResponseSecurityHeaderFilter        Both       OptimizeTomcatConfig  │
│  (default)          ResponseTimezoneFilter              Both       OptimizeTomcatConfig  │
│  (default)          URLRedirectFilter                   Both       OptimizeTomcatConfig  │
│  (default)          NoCachingFilter                     Both       FilterBeansConfig     │
│  (default)          CCSMRequestAdjustmentFilter         CCSM only  CCSMSecurityConfig    │
│  ── Spring Security filter chain boundary ──────────────────────────────────────────────│
│  (inside chain)     ApiBearerTokenAuthenticationFilter  Both       AbstractSecConfig     │
│  (inside chain)     CCSMAuthenticationCookieFilter      CCSM only  CCSMSecurityConfig    │
│  (inside chain)     AuthenticationCookieFilter          SaaS only  CCSaaSSecurityConfig  │
│  ── DispatcherServlet ───────────────────────────────────────────────────────────────────│
│  (postHandle)       CacheRequestInterceptor             Both       OptimizeWebMvcConfig  │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

> **Note on "default" order:** `FilterRegistrationBean` defaults to `Integer.MAX_VALUE - 5` when
> no explicit order is set. Filters at this order run after all Spring-managed ordered beans but
> before the Spring Security `FilterChainProxy` (which runs at `Integer.MAX_VALUE`). The relative
> order among the "default" filters above follows Spring Boot's bean definition order within each
> `@Configuration` class.
>
> **Note on `CCSaasRequestAdjustmentFilter`:** It is registered at `Ordered.HIGHEST_PRECEDENCE`
> (`Integer.MIN_VALUE`), meaning it runs **before every other filter**, including the security
> headers filter. This is intentional: the cluster-ID prefix must be stripped from the URI before
> any other component reads it, including the `URLRedirectFilter` and Spring Security.

---

## Servlet Filters

### 1. `CCSaasRequestAdjustmentFilter`

|     Property      |                        Value                        |
|-------------------|-----------------------------------------------------|
| **Package**       | `io.camunda.optimize.tomcat`                        |
| **Registered in** | `CCSaaSSecurityConfigurerAdapter.requestAdjuster()` |
| **Order**         | `Ordered.HIGHEST_PRECEDENCE` (`Integer.MIN_VALUE`)  |
| **URL pattern**   | `/*`                                                |
| **Mode**          | **SaaS only**                                       |

**What it does:**

1. **Home-page redirect** — If the URI is exactly `/<clusterId>` (no trailing slash), responds
   with a redirect to `/<clusterId>/` so the React SPA bootstraps correctly.

2. **Static external resource serving** — If the request is a `GET` for a non-API path under
   `/<clusterId>/external/`, reads the file directly from `ServletContext` and writes it to the
   response, bypassing Spring entirely. Delegates to `ExternalResourcesUtil`.

3. **Cluster-ID prefix stripping** — Removes the leading `/<clusterId>` segment from the URI
   (e.g. `/abc-123/api/report` → `/api/report`) and wraps the request in a
   `PathRewritingRequestWrapper` so every downstream component — Spring Security,
   `DispatcherServlet`, controllers — sees the clean path.

4. **External-API rewrite** — After stripping the cluster-ID, transforms
   `/external/api/…` → `/api/external/…` so the internal REST handler at `/api/external/**`
   is reached by the public-facing URL `/external/api/**`.

**Why it must run first:**
Running at `HIGHEST_PRECEDENCE` ensures the URI is normalised before `URLRedirectFilter` applies
its allow-list regex, before `ResponseSecurityHeaderFilter` reads the URI for CSP decisions, and
— most critically — before Spring Security's `PathPatternRequestMatcher` evaluates the URI for
authentication rules. If this filter ran later, Security would match the raw
`/<clusterId>/api/…` paths against rules written for `/api/…` and everything would fail.

**Why `PathRewritingRequestWrapper` instead of `RequestDispatcher.forward()`:**
Spring Security 7 migrated to `PathPatternParser`. Its `PathPatternRequestMatcher` reads
`HttpServletRequest.getRequestURI()` from the **original** request object the moment the Security
filter chain starts. A `forward()` dispatch is processed too late — Security has already matched
against the original URI. Wrapping the request overrides `getRequestURI()` at the object level,
so every reader of that method (Security, MVC, Tomcat) sees the rewritten path from the very
first access.

---

### 2. `ResponseSecurityHeaderFilter`

|     Property      |                      Value                       |
|-------------------|--------------------------------------------------|
| **Package**       | `io.camunda.optimize.tomcat`                     |
| **Registered in** | `OptimizeTomcatConfig.responseHeadersInjector()` |
| **Order**         | default (`Integer.MAX_VALUE - 5`)                |
| **URL pattern**   | `/*`                                             |
| **Mode**          | Both                                             |

**What it does:**
Reads `SecurityConfiguration.ResponseHeaders` from `ConfigurationService` and adds the configured
HTTP security response headers (e.g. `Content-Security-Policy`, `X-Frame-Options`,
`Strict-Transport-Security`, `X-Content-Type-Options`) to every response before the response
body is written.

**Why it exists:**
Security headers must be present on every response including error pages and redirects. Setting
them in a filter guarantees they are applied before any response body is committed, regardless of
which downstream component writes the body.

---

### 3. `ResponseTimezoneFilter`

|     Property      |                      Value                      |
|-------------------|-------------------------------------------------|
| **Package**       | `io.camunda.optimize.tomcat`                    |
| **Registered in** | `OptimizeTomcatConfig.responseTimezoneFilter()` |
| **Order**         | default (`Integer.MAX_VALUE - 5`)               |
| **URL pattern**   | `/*`                                            |
| **Mode**          | Both                                            |

**What it does:**
Reads the `X-Optimize-Client-Timezone` request header, resolves it to a `ZoneId`, and stores it
as a request-scoped attribute in `RequestContextHolder` under the key
`RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE`. REST controllers and service-layer code retrieve
this attribute to format date/time values in the client's local timezone.

**Why it exists:**
Centralises timezone extraction in one place so that every controller automatically has access to
the client timezone without parsing the header themselves. It must run before
`DispatcherServlet` so the attribute is available during controller method execution.

---

### 4. `URLRedirectFilter`

|     Property      |                 Value                  |
|-------------------|----------------------------------------|
| **Package**       | `io.camunda.optimize.tomcat`           |
| **Registered in** | `OptimizeTomcatConfig.urlRedirector()` |
| **Order**         | default (`Integer.MAX_VALUE - 5`)      |
| **URL pattern**   | `/*`                                   |
| **Mode**          | Both                                   |

**What it does:**
Acts as the gatekeeper for the Single-Page Application (SPA). It processes the URI after
stripping the Tomcat `contextPath` prefix, then applies the following rules in order:

1. If the URI (after context-path) is **empty** → redirect to `<contextPath>/#`.
2. If the URI is **`/`** → pass through (SPA root, serve `index.html`).
3. If the URI resolves to a **static file** present in `/webapp/` on the classpath → pass through.
4. If the URI matches the **`ALLOWED_URL_EXTENSION` regex** (see `OptimizeTomcatConfig`) → redirect
   to `<contextPath>/#` (the regex is a negative-lookahead that matches URIs that are *not* in the
   allow-list).
5. **Otherwise** → pass through to Spring (valid API/actuator/auth endpoint).

The allow-list in `ALLOWED_URL_EXTENSION` includes: `/#`, `/login`, `/metrics`,
all OAuth endpoints, health/readiness paths, `/external`, `/api`, `/static`, `/actuator`,
`/favicon.ico`, `/index.html`, and a few others.

**Why it exists:**
Optimize is a SPA. Any URL that the React router would handle (e.g. `/dashboard/1`,
`/report/abc`) must be redirected to `/#` so the SPA loads and handles routing client-side.
Without this filter, unknown paths would fall through to Spring MVC and return a 404.

**Known coupling with SaaS mode:**
In SaaS mode, `CCSaasRequestAdjustmentFilter` runs first (at `HIGHEST_PRECEDENCE`) and strips the
`clusterId` prefix. By the time this filter runs, the URI is already clean (no `/<clusterId>`
prefix), so the allow-list regex and static-file lookup work correctly in both modes.

**Known coupling with CCSM mode:**
In CCSM mode, Tomcat sets `contextPath` (e.g. `/optimize`). This filter explicitly strips the
`contextPath` from the URI before applying its rules, so the allow-list does not need to include
the context-path prefix.

---

### 5. `NoCachingFilter`

|       Property       |                         Value                         |
|----------------------|-------------------------------------------------------|
| **Package**          | `io.camunda.optimize.tomcat`                          |
| **Registered in**    | `FilterBeansConfig.noCachingFilterRegistrationBean()` |
| **Order**            | default (`Integer.MAX_VALUE - 5`)                     |
| **URL pattern**      | `/*`                                                  |
| **Dispatcher types** | `REQUEST`, `FORWARD`, `ERROR`, `ASYNC`                |
| **Mode**             | Both                                                  |

**What it does:**
Sets the `Cache-Control: no-store` response header for:
- Any response to a REST API call (URI starts with `/api`).
- Responses for specific static resources that must not be cached across upgrades:
`/` and `/index.html` (defined in `OptimizeResourceConstants.NO_CACHE_RESOURCES`).

**Why it exists:**
After an Optimize upgrade, browsers may serve stale JavaScript/CSS bundles from the cache,
causing the UI to break. Marking the HTML entry-points and all API responses as non-cacheable
forces the browser to always fetch fresh content. This filter runs on `FORWARD` and `ERROR`
dispatches too (unlike other filters) to ensure the header is set even on error responses and
internal forwards to `index.html`.

---

### 6. `CCSMRequestAdjustmentFilter`

|     Property      |                       Value                       |
|-------------------|---------------------------------------------------|
| **Package**       | `io.camunda.optimize.tomcat`                      |
| **Registered in** | `CCSMSecurityConfigurerAdapter.requestAdjuster()` |
| **Order**         | default (no explicit order set)                   |
| **URL pattern**   | `/*`                                              |
| **Mode**          | **CCSM only**                                     |

**What it does:**

1. **External-API rewrite** — Transforms `/external/api/…` → `/api/external/…` using
   `PathRewritingRequestWrapper`, preserving the Tomcat `contextPath` prefix in the
   rewritten URI. This maps the public-facing URL `/external/api/**` to the internal
   REST handler at `<contextPath>/api/external/**`.

2. **Static external resource serving** — If the request is a `GET` for a non-API path under
   `/external/`, reads the file from `ServletContext` and writes it directly to the response.
   Delegates to `ExternalResourcesUtil`.

**Why it exists:**
In CCSM there is no cluster-ID to strip. However, Optimize still needs to expose public-share
resources at `/external/api/**` (the "external" URL pattern) while the actual Spring controllers
live at `/api/external/**`. This filter bridges that gap using the same
`PathRewritingRequestWrapper` technique required by Spring Security 7.

**Why `contextPath` is handled explicitly:**
Tomcat sets `contextPath` on the request before any filter runs. `ExternalResourcesUtil` strips
it before doing file lookups, and the rewritten URI re-prepends it so that downstream Spring
components still see the full absolute URI including the context path.

---

## Filters Inside the Spring Security Chain

These are not registered as standalone servlet filters via `FilterRegistrationBean`. Instead they
are inserted into Spring Security's internal `FilterChainProxy` and run only after the Security
chain has been entered (i.e. after all `FilterRegistrationBean` filters above have run).

### `CCSMAuthenticationCookieFilter`

|       Property        |                                    Value                                     |
|-----------------------|------------------------------------------------------------------------------|
| **Registered in**     | `CCSMSecurityConfigurerAdapter.configureWebSecurity()` via `addFilterBefore` |
| **Position in chain** | Before `AbstractPreAuthenticatedProcessingFilter`                            |
| **Mode**              | **CCSM only**                                                                |

Extracts the Optimize session cookie, verifies the JWT access token inside it via
`CCSMTokenService` (which delegates to the Identity/Keycloak JWKS endpoint), and sets the
authenticated principal in the `SecurityContext`. On token expiry it attempts a refresh;
on failure it clears the cookie and returns `401`.

### `AuthenticationCookieFilter`

|       Property        |                                     Value                                      |
|-----------------------|--------------------------------------------------------------------------------|
| **Registered in**     | `CCSaaSSecurityConfigurerAdapter.configureWebSecurity()` via `addFilterBefore` |
| **Position in chain** | Before `OAuth2AuthorizationRequestRedirectFilter`                              |
| **Mode**              | **SaaS only**                                                                  |

Same responsibility as `CCSMAuthenticationCookieFilter` but for the SaaS (Auth0) cookie scheme.
Reads the Optimize auth cookie set after a successful OAuth2 login, validates the session token
via `SessionService`, and populates the `SecurityContext`.

---

## MVC Interceptor (Post-Handler)

### `CacheRequestInterceptor`

|     Property      |                                        Value                                         |
|-------------------|--------------------------------------------------------------------------------------|
| **Package**       | `io.camunda.optimize.tomcat`                                                         |
| **Registered in** | `OptimizeWebMvcConfigurer.addInterceptors()`                                         |
| **Type**          | `HandlerInterceptor` (not a servlet filter)                                          |
| **Phase**         | `postHandle` — after the controller method returns, before the response is committed |
| **Mode**          | Both                                                                                 |

**What it does:**
For successful (`2xx`) responses from controller methods annotated with `@CacheRequest`, adds a
`Cache-Control: max-age=<seconds-until-midnight>` response header. This allows stable
configuration responses (e.g. UI config, localisation) to be cached by the browser until
midnight, reducing redundant API calls during a session.

**Why it is a `HandlerInterceptor` and not a servlet filter:**
`HandlerInterceptor.postHandle` receives the resolved `HandlerMethod`, which is needed to inspect
the `@CacheRequest` annotation. Servlet filters do not have access to the resolved handler; they
only see the raw `HttpServletRequest`.

---

## Support Classes

### `PathRewritingRequestWrapper`

Not a filter itself. An `HttpServletRequestWrapper` used by both request adjustment filters to
override path-related request methods. It overrides:

|            Method            |                                                                             Reason                                                                             |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `getRequestURI()`            | Returns the rewritten URI. Delegates to `super` during `FORWARD` dispatches to avoid an infinite forward loop via `WelcomePageHandlerMapping`.                 |
| `getRequestURL()`            | Rebuilt from `getRequestURI()` to stay consistent with the rewritten path.                                                                                     |
| `getRequestDispatcher(path)` | Converts relative dispatch paths (e.g. `index.html`) to absolute paths based on the rewritten URI so `WelcomePageHandlerMapping` forwards to the right target. |
| `getServletPath()`           | **Not overridden** — overriding it breaks Tomcat's internal resource resolution during `FORWARD` dispatches.                                                   |

### `ExternalResourcesUtil`

Not a filter itself. A shared utility used by both request adjustment filters to:
- Determine whether a request targets a static external resource (`/external/**`, non-API, `GET` only).
- Read that file from `ServletContext` and write it directly to the response, bypassing Spring MVC.
- Strip `contextPath` and `clusterId` prefixes from file paths before doing servlet-context lookups.

---

## Known Issues & Design Notes

### `URLRedirectFilter` and `CCSaasRequestAdjustmentFilter` ordering

`CCSaasRequestAdjustmentFilter` runs at `HIGHEST_PRECEDENCE` and strips the `clusterId` before
`URLRedirectFilter` runs. This is correct and intentional. In CCSM mode there is no cluster-ID,
so `URLRedirectFilter` sees the raw URI minus the `contextPath` (which it strips itself).

### No explicit ordering between "default-order" filters

`ResponseSecurityHeaderFilter`, `ResponseTimezoneFilter`, `URLRedirectFilter`, `NoCachingFilter`,
and `CCSMRequestAdjustmentFilter` all use the default `FilterRegistrationBean` order. Their
relative execution order is determined by Spring Boot's bean definition order, which follows the
order of `@Bean` method declarations within each `@Configuration` class. This is fragile: adding
a new filter without an explicit order could silently change the execution order of existing ones.

**Recommendation:** Assign explicit `setOrder()` values to all `FilterRegistrationBean` beans.

### The ideal long-term solution for SaaS path stripping

`CCSaasRequestAdjustmentFilter` is essentially an in-process reverse proxy that strips the
`clusterId` prefix. The correct infrastructure-level solution is an external reverse proxy
(NGINX, Envoy, Kong) that strips the prefix *before* the request reaches Optimize. This would
eliminate `CCSaasRequestAdjustmentFilter` entirely and remove the `PathRewritingRequestWrapper`
complexity for the SaaS case. The CCSM filter would still be needed for the
`/external/api/` → `/api/external/` rewrite until that mapping is consolidated.
