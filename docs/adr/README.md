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
`authentication/`, `identity/`), or `identity` (covers OIDC authentication and the Identity REST
API specifically — decisions narrow enough to `identity/` that they don't need the broader
`security` domain). A decision belongs at domain level when it is too broad for a single module but
does not affect the entire monorepo. Create the domain directory when the first ADR for that domain
is written.

## Index

- `identity/0001-multi-jwks-endpoints-per-issuer.md` — support multiple JWKS endpoints per OIDC
  issuer, so token verification succeeds when an identity provider distributes signing keys across
  more than one JWKS URI for the same issuer.
- `identity/0002-support-forward-slashes-in-entity-ids.md` — support forward slashes in Identity
  entity IDs (e.g. Keycloak group IDs like `/org/team/engineering`) via URL encoding, so OIDC-sourced
  entities with `/` in their ID can be managed through the REST API and the Identity UI
  (camunda/camunda#45215).
- `identity/0003-userinfo-claim-augmentation-for-bearer-tokens.md` — augment bearer-token
  authentication with an optional `/userinfo` call so authorization-relevant claims (groups, roles,
  tenants) are available even when an identity provider omits them from the access token itself.

