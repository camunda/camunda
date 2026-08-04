# Architecture Decision Records

This directory contains ADRs for decisions that affect multiple modules across the monorepo.

## Tiers

|                            Scope                            |                    Location                     |
|-------------------------------------------------------------|-------------------------------------------------|
| Monorepo-wide (affects the whole repo or its core design)   | `docs/adr/` — this directory                    |
| Domain (spans a few related components, not the whole repo) | `docs/adr/<domain>/` — e.g. `docs/adr/storage/` |
| Module-scoped                                               | `<module>/docs/adr/`                            |

A **domain** is a logical subsystem that groups a few related components — for example, `storage`
(covers `search/`, `operate/schema/`, `tasklist/els-schema/`) or `auth` (covers `authentication/`,
`identity/`). A decision belongs at domain level when it is too broad for a single module but does
not affect the entire monorepo. Create the domain directory when the first ADR for that domain is
written.
