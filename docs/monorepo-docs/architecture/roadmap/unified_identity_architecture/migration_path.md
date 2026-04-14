# Unified Identity Architecture – Migration Path

This document describes the incremental migration path from the current split identity systems
to the unified Security Gateway Framework described in the
[Unified Identity Architecture](unified_identity_architecture.md).

## 1. Current state summary

Before describing the migration, the following table summarizes the components being replaced per deployment context.

| Deployment | Component | Responsible for | Storage |
|---|---|---|---|
| SaaS | Auth0 (Camunda-managed) | Management plane AuthN (Console, Web Modeler) | Auth0 tenant |
| SaaS | OC Identity ([identity_architecture_docs.md](../../components/identity/identity_architecture_docs.md)) | Runtime AuthN/AuthZ (Operate, Tasklist, OC APIs) | Zeebe primary (RocksDB) + secondary (ES/OS/RDBMS) |
| Self-Managed | Management Identity ([management_identity_architecture_docs.md](../../components/identity/management_identity_architecture_docs.md)) | Platform app AuthN/AuthZ (Console, Web Modeler, Optimize) | Keycloak DB + Management Identity PostgreSQL |
| Self-Managed | OC Identity ([identity_architecture_docs.md](../../components/identity/identity_architecture_docs.md)) | Runtime AuthN/AuthZ (Operate, Tasklist, OC APIs) | Zeebe primary (RocksDB) + secondary (ES/OS/RDBMS) |

**What does not change:** all enterprise IdP integrations remain standard OIDC/SAML. The customer's
IdP (Keycloak, Entra, Okta, etc.) is never replaced — only the components consuming and enforcing
identity decisions change.

WIP
