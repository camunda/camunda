```yaml
---
applyTo: "zeebe/auth/**"
---
```
# Zeebe Auth Module

## Purpose

`zeebe/auth` is a lightweight, dependency-minimal module providing JWT decoding utilities and authorization claim key constants used across the Zeebe engine, gateways, exporters, and security layers. It defines the canonical claim keys that flow through the entire authorization pipeline — from gateway request interception through engine processing to exporter audit logging.

## Architecture

This module contains exactly two classes in `io.camunda.zeebe.auth`:

- **`Authorization`** — String constants defining canonical claim keys for the authorization claims map (`Map<String, Object>`) that is attached to every broker request and engine record. These keys are the shared contract between the gateway layer (which populates claims), the engine (which reads claims for authorization checks), and the exporters (which extract actor identity for audit logs).
- **`JwtDecoder`** — Stateful JWT token decoder wrapping `com.auth0.java-jwt`. Decodes a JWT string into a `Map<String, Object>` of claims with type coercion (booleans, ints, longs, doubles, lists, nested JSON objects). Used by `AuthInfo.toDecodedMap()` in `zeebe/protocol-impl` to decode JWT-format auth data on the broker side.

## Claim Key Constants (`Authorization`)

| Constant | Value | Purpose |
|----------|-------|---------|
| `AUTHORIZED_ANONYMOUS_USER` | `"authorized_anonymous_user"` | Boolean flag: `true` when no authenticated identity |
| `AUTHORIZED_TENANTS` | `"authorized_tenants"` | List of tenant IDs the caller is authorized for |
| `AUTHORIZED_USERNAME` | `"authorized_username"` | Authenticated user's username |
| `AUTHORIZED_CLIENT_ID` | `"authorized_client_id"` | Authenticated M2M client ID |
| `USER_TOKEN_CLAIMS` | `"user_token_claims"` | Raw OIDC token claims map |
| `USER_GROUPS_CLAIMS` | `"user_groups_claims"` | Group IDs from OIDC claims (when Camunda groups are disabled) |
| `IS_CAMUNDA_USERS_ENABLED` | `"is_camunda_users_enabled"` | Whether Camunda-managed users are active |
| `IS_CAMUNDA_GROUPS_ENABLED` | `"is_camunda_groups_enabled"` | Whether Camunda-managed groups are active |

## Consumer Modules

This module is consumed as a compile dependency by:
- **`zeebe/protocol-impl`** (`AuthInfo.java`) — uses `JwtDecoder` to decode JWT-format auth data attached to broker requests
- **`zeebe/engine`** — `ClaimsExtractor` reads claim keys for authorization resolution; `UserTaskAssignProcessor` reads username
- **`zeebe/gateway-grpc`** — `EndpointManager` populates claims map on outbound broker requests
- **`zeebe/exporter-common`** — `AuditLogInfo.AuditLogActor` extracts actor identity from record authorizations
- **`security/security-core`** — `BrokerRequestAuthorizationConverter` maps `CamundaAuthentication` to claims map using these constants

It is a test-scoped dependency in `service/`, `security/security-services/`, `zeebe/exporters/rdbms-exporter/`, and `zeebe/exporters/camunda-exporter/`.

## Data Flow

1. **Gateway** (`EndpointManager`, `BrokerRequestAuthorizationConverter`): Populates a `Map<String, Object>` using `Authorization.*` keys from the authenticated Spring Security context.
2. **Wire transport** (`AuthInfo` in `zeebe/protocol-impl`): Serializes claims to MsgPack (`AuthDataFormat.PRE_AUTHORIZED`) or stores JWT token string (`AuthDataFormat.JWT`).
3. **Broker/Engine** (`ClaimsExtractor`, authorization processors): Reads claims from the record's authorization map using `Authorization.*` keys to resolve permissions.
4. **Exporters** (`AuditLogInfo`): Extracts actor identity from `record.getAuthorizations()` using `Authorization.*` keys for audit trail.

## Extension Points

- **Adding a new claim key**: Add a `public static final String` constant to `Authorization.java`. Then update all producers (gateway converters) and consumers (engine `ClaimsExtractor`, exporter `AuditLogInfo`).
- **Changing JWT decoding behavior**: Modify `JwtDecoder.convertClaimValue()`. The type coercion order matters — it tries JSON object first, then boolean, int, long, double, list, and falls back to string.

## Invariants

- Every constant in `Authorization` is a `String` used as a map key — never use raw string literals for these keys in consumer modules; always reference `Authorization.*`.
- `JwtDecoder` performs **no signature verification** — it only decodes. Signature validation happens upstream in the gateway's Spring Security filters.
- `JwtDecoder` is not thread-safe — it holds mutable `decodedJwt` state. Create a new instance per decode operation.
- The module must remain dependency-light (only `java-jwt` and `jackson`). It is depended on by low-level protocol modules.

## Common Pitfalls

- Do not add Spring or heavy framework dependencies to this module — it is used by `protocol-impl` and engine modules that must remain Spring-free.
- Do not perform JWT signature verification in `JwtDecoder` — that responsibility belongs to the gateway authentication layer.
- When adding a new claim key, ensure it is set in `BrokerRequestAuthorizationConverter.convert()` and read consistently in `ClaimsExtractor` — mismatches cause silent authorization failures.
- `JwtDecoder.convertClaimValue()` tries `asString()` for JSON parsing first; if a claim value is a plain string that happens to look like JSON, it may be incorrectly parsed as a map.

## Key Files

- `src/main/java/io/camunda/zeebe/auth/Authorization.java` — canonical claim key constants
- `src/main/java/io/camunda/zeebe/auth/JwtDecoder.java` — JWT token-to-claims decoder
- `src/test/java/io/camunda/zeebe/auth/JwtDecoderTest.java` — unit tests for JWT decoding
- `pom.xml` — minimal dependencies: `java-jwt`, `jackson-databind`, `jackson-core`

## Testing

Run module tests with:
```
./mvnw -pl zeebe/auth -am test -DskipITs -DskipChecks -T1C
```
Follow the `// given`, `// when`, `// then` structure. Use AssertJ exclusively. The test jar is published (`maven-jar-plugin` `test-jar` goal) so other modules can reuse test utilities.