# Identity ADRs

Architecture Decision Records scoped to the Identity module — authentication, authorization,
and OIDC integration decisions. Cross-cutting monorepo-wide decisions live in
[`docs/adr/`](/adr).

## Index

|                              ADR                              |                        Decision                         |
|---------------------------------------------------------------|---------------------------------------------------------|
| [0001](0001-cluster-embedded-identity.md)                     | Cluster-embedded Identity instead of external component |
| [0002](0002-oidc-default-production-authentication.md)        | OIDC as default production authentication               |
| [0003](0003-resource-based-authorization-model.md)            | Resource-based authorization model                      |
| [0004](0004-multi-jwks-endpoints-per-issuer.md)               | Support multiple JWKS endpoints per OIDC issuer         |
| [0005](0005-support-forward-slashes-in-entity-ids.md)         | Support forward slashes in entity IDs via URL encoding  |
| [0006](0006-userinfo-claim-augmentation-for-bearer-tokens.md) | UserInfo claim augmentation for OIDC bearer tokens      |

