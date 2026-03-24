# ADR-0005: Support Forward Slashes in Entity IDs via URL Encoding

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

The solution has three layers:

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

### 2. Tomcat: PASSTHROUGH mode for encoded slashes

Tomcat 11 rejects `%2F` in URL paths by default (`EncodedSolidusHandling.REJECT`). A
`WebServerFactoryCustomizer` bean sets the handling mode to `PASSTHROUGH`, which forwards `%2F`
as-is to the application without decoding or rejecting it.

```java
connector.setEncodedSolidusHandling("passthrough");
```

The `DECODE` mode was not chosen because it decodes `%2F` to `/` before Spring MVC sees the
request, which would break route matching in the same way as the original unencoded slash.

### 3. Spring MVC: PathPatternParser (no changes needed)

Spring Boot's default `PathPatternParser` splits paths on literal `/` characters only. An encoded
`%2F` is not a path separator, so `/v2/roles/admin/groups/%2FmyGroup` correctly matches
`/v2/roles/{roleId}/groups/{groupId}`. Spring then decodes `%2F` to `/` when binding to the
`@PathVariable` parameter. This is the default behavior — no configuration changes are required.

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
- Fully backward compatible: `encodeURIComponent` is a no-op on alphanumeric strings, and
  `PASSTHROUGH` only affects requests that contain `%2F`.
- No changes to controllers, service layer, or data model.

### Negative

- **Reverse proxies must forward raw URIs.** Proxies like nginx may reject or decode `%2F` by
  default. Operators using reverse proxies must configure them to preserve encoded characters
  (e.g., nginx: `proxy_pass` with `$request_uri`, or `merge_slashes off`).
- **External API clients must encode IDs.** Any client calling the REST API directly must
  URL-encode entity IDs containing special characters. This is standard HTTP behavior but must be
  documented in the API reference.
- **`PASSTHROUGH` applies to all connectors.** The Tomcat configuration affects all incoming
  requests, not just identity endpoints. This is safe because `%2F` in a path segment is only
  meaningful if the application interprets it — existing endpoints with alphanumeric IDs are
  unaffected.

