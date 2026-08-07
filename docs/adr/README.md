# Architecture Decision Records

This directory contains ADRs for decisions that affect multiple modules across the monorepo.

## Tiers

|                            Scope                            |                    Location                     |
|-------------------------------------------------------------|-------------------------------------------------|
| Monorepo-wide (affects the whole repo or its core design)   | `docs/adr/` — this directory                    |
| Domain (spans a few related components, not the whole repo) | `docs/adr/<domain>/` — e.g. `docs/adr/storage/` |
| Module-scoped                                               | `<module>/docs/adr/`                            |

A **domain** is a logical subsystem that groups a few related components — for example, `storage`
(covers `db/`, `search/`, `webapps-schema/`, `schema-manager/`), `security` (covers `security/`,
`authentication/`), or `identity` (covers `identity/`). A decision belongs at domain level when it
is too broad for a single module but does not affect the entire monorepo. Create the domain
directory when the first ADR for that domain is written.

## Index

- `identity/0001-multi-jwks-endpoints-per-issuer.md` — support multiple JWKS endpoints per OIDC
  issuer via a composite JWK source, for deployments where an issuer's signing keys are split
  across several JWKS endpoints (camunda/product-hub#3472).
- `identity/0002-support-forward-slashes-in-entity-ids.md` — URL-encode entity IDs (group, role,
  tenant, user, mapping rule) that contain forward slashes — e.g. Keycloak group IDs — so they
  survive as REST path segments instead of producing extra, unroutable path segments
  (camunda/camunda#45215).
- `identity/0003-userinfo-claim-augmentation-for-bearer-tokens.md` — for the bearer-token flow
  (REST `/v1`, `/v2`, gRPC via the Zeebe gateway), augment authorization claims with a cached
  `/userinfo` call when an OIDC provider omits them from the access token itself, matching what
  the browser login flow already does.

