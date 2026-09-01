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

## RP (Relying Party)-initiated logout troubleshooting

Both `PostLogoutController` and `CamundaOidcLogoutSuccessHandler` emit logs at the `TRACE` level.
For troubleshooting, examine TRACE-level log entries similar to the following example:

```
Unable to determine end-session endpoint for OIDC logout. Falling back to {baseLogoutUrl} without logout hint.
```

