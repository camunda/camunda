# ADR-0004: Configuration Class Migration Strategy (security-core to gatekeeper)

**Date:** 2026-03-12
**Status:** Proposed

## Context

After migrating `CamundaAuthentication` and `CamundaAuthenticationProvider` to gatekeeper as
canonical types, `security-core` still contains configuration classes that duplicate what gatekeeper
already provides as immutable domain records. We needed a strategy to migrate consumers of these
overlapping config classes without a big-bang change to `SecurityConfiguration` and its 95 consumers.

### Overlap analysis:

|        security-core class        |      gatekeeper equivalent      |                  Match                   |
|-----------------------------------|---------------------------------|------------------------------------------|
| `AuthorizationsConfiguration`     | `AuthorizationConfig` (record)  | Identical (single `enabled` field)       |
| `CsrfConfiguration`               | `CsrfConfig` (record)           | Partial (gatekeeper adds `allowedPaths`) |
| `AuthenticationMethod` (enum)     | `AuthenticationMethod` (enum)   | Identical                                |
| `AuthenticationConfiguration`     | `AuthenticationConfig` (record) | Structural differences                   |
| `OidcAuthenticationConfiguration` | `OidcConfig` (record)           | Significant differences                  |
| `MultiTenancyConfiguration`       | `MultiTenancyConfig` (record)   | Field mismatch                           |

### Out of scope:

`SecurityConfiguration` aggregator (95 consumers), `InitializationConfiguration`,
`SaasConfiguration`, `HeaderConfiguration`, `ProvidersConfiguration`, `AssertionConfiguration`,
`AuthorizeRequestConfiguration`.

## Decision

Use a **thin adapter pattern**: security-core config classes remain as `@ConfigurationProperties`
binding targets (Spring needs mutable setters). Where the domain type is structurally identical,
add a `toGatekeeperConfig()` method on the security-core class that converts to the gatekeeper
record. Read-only consumers switch to using the gatekeeper record.

### Migration phases:

1. **`AuthenticationMethod` enum** (~15 consumers) — direct import swap, identical types
2. **`AuthorizationsConfiguration`** (8 consumers) — single-field record, add `toGatekeeperConfig()`
3. **`CsrfConfiguration`** (2 consumers) — add `toGatekeeperConfig()` with `Set.of()` for
   `allowedPaths`
4. **`MultiTenancyConfiguration`** (35 consumers) — requires research into `checksEnabled` vs
   `apiEnabled` usage before deciding approach
5. **Evaluate deeper migrations** — `AuthenticationConfiguration` and
   `OidcAuthenticationConfiguration` have structural differences that may not justify migration

### Key principles:

- `toGatekeeperConfig()` adapter methods live on security-core classes (correct dependency
  direction: security-core -> gatekeeper-domain, not reverse)
- `SecurityConfiguration` itself is NOT migrated — it remains as the `@ConfigurationProperties`
  binding root
- Each phase is independently verifiable with `mvn compile -T1C -q`

## Consequences

- Incremental migration with no big-bang risk
- `SecurityConfiguration` continues to work unchanged for all 95 consumers
- Consumers that only read config can use immutable gatekeeper records (thread-safe, structural
  equality)
- Some duplication remains intentionally where structural differences make migration costly
- Phase 5 may conclude with "leave as-is" if cost exceeds benefit

