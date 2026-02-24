# CI Security & Compliance

This prompt covers security best practices and third-party action compliance for this monorepo.

## Third-Party GitHub Actions Allowlist

**MANDATORY:** When introducing a new third-party GitHub Action, follow this assess-then-decide flow — do **not** silently discard unapproved options without surfacing them to the reviewer.

### Step 1: Verify Necessity

Identify all options that can solve the requirement: approved actions first, then unapproved candidates.

### Step 2: Check Allowlist

Location: `docs/monorepo-docs/ci.md`
Section: `## CI Security → ### Usage of Third Party GitHub Actions`
Inside: `<details> → <summary>List of allowed 3rd party actions and reusable workflows</summary>`

```bash
# Search for specific action
search "org-or-owner/action-name@" path:docs/monorepo-docs/ci.md
```

### Step 3: Present Options With Trade-offs

Always present **both** the approved-only approach and any unapproved-but-relevant candidates. Let the reviewer decide. Structure the presentation as:

```
## Option A — Approved actions only (safe default)
<workflow sketch>
Trade-offs: [what you gain / what you give up]

## Option B — <org/action-name> (not yet on allowlist)
<workflow sketch>
Trade-offs: [what you gain / what you give up]
Security assessment: [maintainer, stars, last release, permissions required]
To approve: add 'org/action-name@*' to docs/monorepo-docs/ci.md allowlist (alphabetical order)
```

### Step 4: If Reviewer Approves An Unapproved Action

1. **Update documentation** in the same PR:
   - Add entry as: `organization/action-name@*`
   - Insert in **alphabetical order** in `docs/monorepo-docs/ci.md`
2. **Pin to a commit SHA** (never a floating tag)
3. **Security review:** If maintainer trustworthiness is unclear, tag Monorepo CI DRI before merging

## Currently Approved Actions (Examples)

```
actions/*@*           # GitHub official
camunda/*@*           # Camunda organization
docker/*@*            # Docker official
hashicorp/vault-action@*
dorny/paths-filter@*
nick-fields/retry@*
slackapi/slack-github-action@*
```

Full list: `docs/monorepo-docs/ci.md` → CI Security section

## Permissions Best Practices

**Default:** `permissions: {}`

Only add specific permissions when needed:

```yaml
permissions:
  contents: read        # For checkout
  pull-requests: write  # For PR comments
  issues: write         # For issue comments
```

## Secret Management

**Vault is mandatory.** GitHub Secrets allowed ONLY for Vault bootstrap:
- `VAULT_ADDR`
- `VAULT_ROLE_ID`
- `VAULT_SECRET_ID`

### Vault Usage Pattern

```yaml
- name: Import Secrets
  id: secrets
  uses: hashicorp/vault-action@v3
  with:
    url: ${{ secrets.VAULT_ADDR }}
    method: approle
    roleId: ${{ secrets.VAULT_ROLE_ID }}
    secretId: ${{ secrets.VAULT_SECRET_ID }}
    exportEnv: false
    secrets: |
      secret/data/products/camunda/ci/github-actions MY_SECRET;

- name: Use secret
  run: echo "Using ${{ steps.secrets.outputs.MY_SECRET }}"
```

## Security Boundaries

- ✅ **Always:** Pin action SHAs, use Vault for secrets, least-privilege permissions, present trade-offs for unapproved actions
- ⚠️ **Ask first:** OIDC trust boundaries, new deployment targets; surface unapproved actions with assessment rather than silently dropping them
- 🚫 **Never:** `curl | bash`, untrusted remote scripts, embed credentials, broad permissions, skip the allowlist update when an unapproved action is approved

