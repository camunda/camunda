# ADR-0002: Support Forward Slashes in Entity IDs via URL Encoding

## Status

Proposed

## Context

Camunda's Identity REST API uses entity IDs (group, role, tenant, user, mapping rule) as path
segments in URLs — for example, `PUT /v2/roles/{roleId}/groups/{groupId}`. When Camunda is
configured with an OIDC provider such as Keycloak, entity IDs are sourced directly from the
identity provider. Keycloak group IDs commonly contain forward slashes (e.g., `/myGroup`,
`/org/team/engineering`).

When the frontend constructs a URL with a literal `/` in an entity ID, the resulting path (e.g.,
`PUT /v2/roles/admin/groups//myGroup`) contains extra path segments. Spring MVC finds no matching
route and returns 400. This effectively prevents any OIDC-sourced entity with `/` in its ID from
being managed through the REST API or the Identity UI.

Two compounding issues cause this:

1. **Validation allows `/`**: `SecurityConfiguration.DEFAULT_EXTERNAL_ID_REGEX` is `.*` when the
   OIDC `groupsClaim` is configured, permitting any character in group IDs — including `/`.
2. **Frontend does not encode**: URL paths are constructed via template literals without
   `encodeURIComponent`, so special characters are inserted verbatim.

See: https://github.com/camunda/camunda/issues/45215

## Decision

We will use standard URL encoding (`%2F`) on the client side and configure the embedded Tomcat
server to pass encoded slashes through to Spring MVC. Entity IDs will not be normalized or
transformed — they must match exactly what the identity provider returns.

The solution has four layers:

### 1. Frontend: Encode IDs with `encodeURIComponent`

Every entity ID interpolated into a URL path is wrapped with `encodeURIComponent()`. This is
standard HTTP behavior per RFC 3986 and is a no-op for alphanumeric strings, making it safe for
all existing IDs.

```typescript
// Before
apiPut(`${ROLES_ENDPOINT}/${roleId}/groups/${groupId}`);
// After
apiPut(`${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}`);
```

### 2. Tomcat: DECODE mode for encoded slashes

Tomcat 11 rejects `%2F` in URL paths by default (`EncodedSolidusHandling.REJECT`). A
`TomcatConnectorCustomizer` bean sets the handling mode to `DECODE`, which converts `%2F` to a
literal `/` at the connector level before Tomcat's path normalization runs.

```java
connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
```

`PASS_THROUGH` was tried first but does not work on Tomcat 10.1+: the path is still rejected during
path normalization even though the connector itself permits `%2F`. `DECODE` is the only mode that
survives the connector. Route matching continues to work because Spring's `PathPatternParser`
matches the decoded URI without collapsing the resulting `//` boundary, and `@PathVariable` binding
receives the leading-slash value as expected. This is covered by `EncodedSlashIntegrationIT`.

### 3. Spring MVC: PathPatternParser (no changes needed)

After the Tomcat connector decodes `%2F`, Spring sees a path with literal `/` characters (e.g.
`/v2/roles/admin/groups//myGroup`). The default `PathPatternParser` matches the variable segments
greedily and does not collapse the `//` boundary, so `/v2/roles/{roleId}/groups/{groupId}` still
binds correctly with `groupId = "/myGroup"`. No configuration changes are required.

### 4. Spring Security: relax `StrictHttpFirewall`

Spring Security's default `StrictHttpFirewall` rejects any request whose URI contains `%2F` (or its
decoded variants in some forms) with a 400 before the request reaches the controller. A
`WebSecurityCustomizer` bean (`WebSecurityConfig.encodedSlashFirewallCustomizer`) installs a
`StrictHttpFirewall` with `setAllowUrlEncodedSlash(true)`.

```java
@Bean
public WebSecurityCustomizer encodedSlashFirewallCustomizer() {
  final var firewall = new StrictHttpFirewall();
  firewall.setAllowUrlEncodedSlash(true);
  return web -> web.httpFirewall(firewall);
}
```

Spring Security 6.5 removed the autowired `WebSecurityConfigurerAdapter#setHttpFirewall()` setter,
so a plain `@Bean HttpFirewall` is no longer wired automatically — `WebSecurityCustomizer` is the
supported replacement. A single customizer covers all `SecurityFilterChain` beans because the
firewall runs in front of `FilterChainProxy`, ahead of any per-chain matcher.

## Alternatives Considered

### Normalize IDs (strip or replace slashes)

Rejected. Entity IDs must match the identity provider exactly. Normalizing would create a mismatch
between Camunda's stored ID and the ID in OIDC tokens, breaking authorization checks.

### Use request body instead of path variables

Rejected. This would require changing the REST API contract, breaking backward compatibility for
all API consumers. URL encoding is the standard HTTP mechanism for this problem.

### Custom path matching or wildcard patterns

Rejected. Using `**` patterns or custom `PathMatcher` configuration would be fragile, make routing
ambiguous, and require changes across all controllers. Standard URL encoding solves the problem
without any routing changes.

## Consequences

### Positive

- Entity IDs from OIDC providers are supported as-is, regardless of special characters.
- The solution follows HTTP standards (RFC 3986) — no custom encoding schemes.
- Fully backward compatible: `encodeURIComponent` is a no-op on alphanumeric strings, and the
  `DECODE` mode only affects requests that contain `%2F`.
- No changes to controllers, service layer, or data model.

### Negative

- **Reverse proxies must forward raw URIs.** Proxies like nginx may reject or decode `%2F` by
  default. Operators using reverse proxies must configure them to preserve encoded characters
  (e.g., nginx: `proxy_pass` with `$request_uri`, or `merge_slashes off`).
- **External API clients must encode IDs.** Any client calling the REST API directly must
  URL-encode entity IDs containing special characters. This is standard HTTP behavior but must be
  documented in the API reference.
- **`DECODE` applies to all connectors.** The Tomcat configuration affects all incoming requests,
  not just identity endpoints. Likewise, the relaxed Spring Security firewall is global. This is
  safe because `%2F` in a path segment is only meaningful if the application interprets it —
  existing endpoints with alphanumeric IDs are unaffected.

