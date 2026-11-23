---

name: ci-security-compliance
description: Enforces GitHub Actions security and compliance for this monorepo. Use when adding third-party actions, handling secrets, defining permissions, and reviewing CI security trade-offs.
---

# CI Security and Compliance Skill

Consult `docs/monorepo-docs/ci.md` before decisions.

## Third-Party Action Review Flow

1. Verify necessity and alternatives.
2. Check allowlist in `docs/monorepo-docs/ci.md` (CI Security section).
3. Present both options:
   - Option A: approved actions only
   - Option B: unapproved candidate with security assessment
4. If unapproved action is accepted:
   - Add to allowlist in alphabetical order
   - Pin commit SHA in workflow
   - Request DRI review if trustworthiness is unclear

Never silently drop unapproved but relevant options.

## Permissions Rules

Start with:

```yaml
permissions: {}
```

Add only required scopes.

## Secret Management

Vault is mandatory. GitHub Secrets are only for Vault bootstrap values:
- `VAULT_ADDR`
- `VAULT_JWT_ROLE`, `VAULT_JWT_PATH`, `VAULT_JWT_AUDIENCE`

Log in with JWT (`method: jwt`) and grant `id-token: write` to the job that logs in. AppRole (`VAULT_ROLE_ID` / `VAULT_SECRET_ID`) is deprecated: never add it to new or changed steps. A job that runs an AI agent never gets `id-token: write`; a separate job wraps its secrets with `vault-wrapped-secrets`. See the [CI Secret Management](../../../docs/monorepo-docs/ci.md#vault-authentication) section.

## Boundaries

- Always: least privilege, SHA pinning, Vault usage, explicit trade-offs
- Ask first: OIDC trust boundaries, new deployment targets
- Never: embedded credentials, broad permissions, untrusted `curl | bash`

## Reference

- Allowed action guidance: `references/approved-actions.md`

