# RP (Relying Party)-initiated logout

Starting with 8.9.0, when Identity is running with Keycloak/Entra (or any other IdP), RP‑initiated logout can be enabled using the following flag:

```
CAMUNDA_SECURITY_AUTHENTICATION_OIDC_IDPLOGOUTENABLED=<true|false>
```

Or in `.yaml`:

```yaml
camunda:
  security:
    authentication:
      oidc:
        idp-logout-enabled: true|false
```

When set to `true`, logging out from Identity also signs the user out of Keycloak or Entra. When set to `false`, logging out from Identity clears only the Camunda session and leaves the user's Keycloak or Entra session active.
If issuer-uri is not set but RP‑initiated logout is required, configure the end-session-endpoint-uri; for example, for Entra:

```
camunda.security:
  authentication:
    oidc:
      authorization-uri: https://login.microsoftonline.com/<YOUR_TENANT_ID>/oauth2/v2.0/authorize
      token-uri: https://login.microsoftonline.com/<YOUR_TENANT_ID>/oauth2/v2.0/token
      jwk-set-uri: https://login.microsoftonline.com/<YOUR_TENANT_ID>/discovery/v2.0/keys
      end-session-endpoint-uri: https://login.microsoftonline.com/<YOUR_TENANT_ID>/oauth2/v2.0/logout
```

Or using environment variable:

```
CAMUNDA_SECURITY_AUTHENTICATION_OIDC_ENDSESSIONENDPOINTURI=https://login.microsoftonline.com/<YOUR_TENANT_ID>/oauth2/v2.0/logout
```

The behavior of RP‑initiated logout for different browsers is as follows:
* The IdP maintains separate sessions per browser, and these sessions do not affect one another. The same applies to Camunda sessions, so logging out in browser A does not end the session in browser B.
* The logout origin URL is captured after the current Camunda session is terminated. Its value is taken from the Referer request header and stored in a new HTTP session that will later be read by the post-logout controller:
* The `CamundaOidcLogoutSuccessHandler` creates a new HTTP session and returns its `JSESSIONID` to the browser.
* When the browser sends the post-logout request, it includes this `JSESSIONID`.
* Server-side used this `JSESSIONID` to associate HTTP session with current request
* So when you log out of browser A and login in browser B you won't be redirected to the browser A location as these are two separate systems and sessions that don't affect each other
* Incognito mode also acts as a separate browser

## Configure the post-logout redirect URL in the IdP

In 8.9.0, the new `PostLogoutController` handles post-logout redirection after RP-initiated logout. By default, it redirects to the Identity home page (/). To use a custom URL, configure `/post-logout` in the IdP's list of allowed post-logout redirect URLs—for example, in Keycloak this is set in the client's post-logout redirect URIs:

```
http://localhost:8080/post-logout
```

If this is set, the next login opens the same page the user logged out from, rather than the home page.
For troubleshooting, the logout origin URL is stored in the `CamundaOidcLogoutSuccessHandler` class, where it is read from `request.getHeader("referer")`.
Propagation of the `logout_hint` parameter is also handled in this class.

### Disabling the post-logout redirect

By default, RP-initiated logout asks the IdP to send the browser back to the host's `/post-logout`
route afterwards, by submitting it as `post_logout_redirect_uri`. Turn that off with:

```
CAMUNDA_SECURITY_AUTHENTICATION_OIDC_POSTLOGOUTREDIRECTENABLED=false
```

Or in `.yaml`:

```yaml
camunda:
  security:
    authentication:
      oidc:
        post-logout-redirect-enabled: false
```

Do this when the IdP cannot have the resulting URL registered as an allowed post-logout redirect.
Auth0, for example, matches `post_logout_redirect_uri` against its *Allowed Logout URLs* exactly and
supports wildcards only in the subdomain position (`https://*.example.com`), never in the path — so a
deployment served under a per-cluster or per-tenant path prefix, such as
`https://<host>/<clusterId>/post-logout`, has no entry that can ever match it, and Auth0 rejects the
whole end-session request with `invalid_request` instead of logging the user out.

This is orthogonal to `idp-logout-enabled`: that decides whether to contact the IdP at all, this
decides only whether to ask it for a redirect back. Disabling it still terminates the IdP session —
the IdP renders its own logged-out page rather than returning the browser to `PostLogoutController`,
so the user is not sent back to the page they logged out from.

### Choosing the post-logout redirect URL

Disabling the redirect is the blunt option: it trades the return journey for a logout that works.
When the IdP *will* accept some URL — just not the one Camunda composes — name that URL instead:

```yaml
camunda:
  security:
    authentication:
      oidc:
        post-logout-redirect-uri: https://accounts.example.com/logged-out
```

Or using an environment variable:

```
CAMUNDA_SECURITY_AUTHENTICATION_OIDC_POSTLOGOUTREDIRECTURI=https://accounts.example.com/logged-out
```

How the value is read depends on its first character:

|             Configured value              |    What Camunda sends as `post_logout_redirect_uri`     |
|-------------------------------------------|---------------------------------------------------------|
| unset (default)                           | `<base URL>` + the cluster's base path + `/post-logout` |
| `/goodbye`                                | `<base URL>` + the cluster's base path + `/goodbye`     |
| `https://accounts.example.com/logged-out` | that URL, verbatim                                      |
| `{baseUrl}/post-logout`                   | `<base URL>` + `/post-logout` — **no base path**        |

The last two rows are what make this useful for Auth0. A deployment served under a per-cluster path
prefix produces `https://<host>/<clusterId>/post-logout` by default, and Auth0 has no *Allowed
Logout URLs* entry that can ever match it. A value that skips the prefix can be registered once and
matched exactly.

Besides `{baseUrl}`, a template may use `{baseScheme}`, `{baseHost}`, `{basePort}`, `{basePath}` and
`{registrationId}`. Any other placeholder is rejected at startup, as is a value that is neither an
absolute URL, nor a path starting with `/`, nor a template. Whatever URL you choose must still be
registered with the IdP as an allowed post-logout redirect.

Setting `post-logout-redirect-enabled: false` alongside a URI wins: no `post_logout_redirect_uri` is
sent, and a warning is logged.

### Configuring each IdP separately (BYO IdP)

Both `post-logout-redirect-uri` and `post-logout-redirect-enabled` are resolved **per OIDC
provider**. With several IdPs configured, each one carries its own answer and none affects the
others — so a single strict IdP no longer costs every other IdP its redirect:

```yaml
camunda:
  security:
    authentication:
      method: oidc
      providers:
        oidc:
          keycloak:
            client-id: camunda-keycloak
            client-secret: ${KEYCLOAK_SECRET}
            issuer-uri: https://keycloak.example.com/realms/camunda
            # nothing set: keeps the default /post-logout route

          auth0:
            client-id: camunda-auth0
            client-secret: ${AUTH0_SECRET}
            issuer-uri: https://example.eu.auth0.com/
            # one entry in Auth0's Allowed Logout URLs covers every cluster on this host
            post-logout-redirect-uri: "{baseUrl}/post-logout"

          entra:
            client-id: camunda-entra
            issuer-uri: https://login.microsoftonline.com/<TENANT_ID>/v2.0
            # send no post_logout_redirect_uri at all for this one
            post-logout-redirect-enabled: false
```

**The map key is the registration ID.** `keycloak`, `auth0` and `entra` above are the identifiers
the resolved URL is looked up under when a user logs out, matched against the provider that user
actually signed in with. Nothing else connects the two, so the keys are not free-form labels.

For a cluster served at `https://camunda.example.com/abc123/`, that configuration produces:

| User signed in via |         `post_logout_redirect_uri` sent          |
|--------------------|--------------------------------------------------|
| `keycloak`         | `https://camunda.example.com/abc123/post-logout` |
| `auth0`            | `https://camunda.example.com/post-logout`        |
| `entra`            | *(none)*                                         |

Register each value with its own IdP — Auth0's *Allowed Logout URLs*, Keycloak's client post-logout
redirect URIs — using the URL the table shows, not the configured value.

The `auth0` entry is the case worth copying. `{baseUrl}` keeps the host dynamic but drops the
`/abc123` cluster prefix, so a single registered entry matches every cluster on that host; a value
starting with `/` would have kept the prefix and needed one entry per cluster. Quote the `{...}`
form in YAML, or it parses as a map rather than a string.

The same keys work on the flat `camunda.security.authentication.oidc.*` block, which counts as one
more provider — keyed by its `registration-id` (default `oidc`) — and gets its own independent
answer. A deployment can therefore set a URI on the flat block and a different one, or none, on each
`providers.oidc.<id>` entry:

```yaml
camunda:
  security:
    authentication:
      oidc:
        client-id: camunda-primary
        issuer-uri: https://keycloak.example.com/realms/camunda
        post-logout-redirect-uri: "{baseUrl}/post-logout"
      providers:
        oidc:
          auth0:
            client-id: camunda-auth0
            issuer-uri: https://example.eu.auth0.com/
            post-logout-redirect-enabled: false
```

Prefer YAML for the multi-provider form. The environment-variable equivalent
(`CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_AUTH0_POSTLOGOUTREDIRECTURI`) relies on Spring's
relaxed binding to recover the map key from the variable name, which is fragile for anything but
short lowercase alphanumeric keys. The flat block's
`CAMUNDA_SECURITY_AUTHENTICATION_OIDC_POSTLOGOUTREDIRECTURI` has no such ambiguity.

## RP (Relying Party)-initiated logout troubleshooting

Both `PostLogoutController` and `CamundaOidcLogoutSuccessHandler` emit logs at the `TRACE` level.
For troubleshooting, examine TRACE-level log entries similar to the following example:

```
Unable to determine end-session endpoint for OIDC logout. Falling back to {baseLogoutUrl} without logout hint.
```

