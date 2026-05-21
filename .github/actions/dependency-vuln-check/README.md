# Dependency Vulnerability Gate

A CI quality gate that prevents PRs from introducing known vulnerable dependencies.

Uses the **GitHub Dependency Review API** to diff dependency manifests between the PR base and
head, then applies blocking rules defined in [check.py](check.py).

## Blocking rules

| Condition | Blocked? |
|-----------|----------|
| Newly added runtime dep with a vulnerability that has a fix available (any severity) | ✅ Yes |
| Newly added runtime dep with a high or critical vulnerability (even if no fix exists) | ✅ Yes |
| Vulnerability in a development/test-scoped dep | ❌ No (non-blocking notice) |
| Vulnerability covered by `allow-ghsas` in the config | ❌ No (non-blocking warning) |
| Removed or unchanged dep | ❌ No |

Version downgrades are covered: when a dep is downgraded, the new (lower) version appears as
`added` in the API diff and is evaluated normally.

## How it works

```
PR opens / push to PR
        │
        ▼
detect-changes: deps-changed?
        │ yes
        ▼
pr-vuln-check (ci.yml)
        │
        ├─ actions/checkout
        └─ dependency-vuln-check  ← THIS ACTION
                │
                ├─ GitHub Dependency Review API
                │   GET /repos/{owner}/{repo}/dependency-graph/compare/{base}...{head}
                │   → list of added/removed deps with attached vulnerability data
                │
                ├─ For each added runtime dep with vulns:
                │   ├─ check allow-ghsas (dependency-review-config.json)
                │   ├─ determine fix availability (first_patched_version field;
                │   │   fallback: GitHub Advisory GraphQL API)
                │   └─ apply blocking rules (see table above)
                │
                ├─ Posts (or updates) PR comment with findings table
                ├─ Writes step summary
                └─ Exits 1 if any blocking findings
```

## When does this gate run?

In `ci.yml`, `pr-vuln-check` is triggered when all of the following are true:

- Event is `pull_request`
- `detect-changes` output `deps-changed == true` (at least one dependency manifest changed)
- Head ref does not start with `backport` (backport PRs are excluded)
- PR author is not `monorepo-devops-automation[bot]` (Renovate is excluded — it has its own gate)

### `deps-changed` paths filter

The `detect-changes` job flags `deps-changed` when any of these paths change:

- `**/pom.xml`
- `bom/**`
- `parent/**`
- `**/package-lock.json`
- `**/yarn.lock`
- `**/package.json`
- `clients/go/go.sum`
- `clients/go/go.mod`
- `Dockerfile`

## PR comment

When findings are detected, the action posts (or updates) a single PR comment identified by the
hidden marker `<!-- dependency-vuln-check -->`:

**Blocking findings** appear under a `🚨` header:

| Package | File | Ecosystem | Severity | Rule | Fix | Advisory |
|---------|------|-----------|----------|------|-----|----------|
| `example:1.9` | `zeebe/pom.xml` | maven | critical | fixable | `2.0.1` | [CVE-2022-XXXX](…) |

**Allowed exceptions** (covered by `allow-ghsas`) appear under a `⚠️` header — visible but not blocking.

**Dev/test-scoped** findings appear under a separate `⚠️` header — visible but not blocking.

On subsequent pushes the comment is updated in-place rather than duplicated.

## Configuration — exceptions

Add GHSA IDs to `.github/dependency-review-config.json` under `allow-ghsas` to suppress
blocking for a specific advisory:

```json
{
  "allow-ghsas": [
    "GHSA-xxxx-xxxx-xxxx"
  ]
}
```

Each entry should be accompanied by an `_comment` or a tracking issue explaining the reason and
a review-by date. The file is CODEOWNERS-protected — requires `@camunda/monorepo-devops-team`
review to add or modify exceptions.

## Pilot phase

The `pr-vuln-check` job currently runs with `continue-on-error: true`. Findings are visible in
the PR comment and CI step summary but do not block merge. Remove that flag once existing
findings across open PRs have been triaged.

## CI job

Defined in `.github/workflows/ci.yml` as `pr-vuln-check`:

- **Runs on:** `ubuntu-latest`
- **Timeout:** 10 minutes
- **Permissions:** `contents: read`, `pull-requests: write`
- **Part of:** `check-results` aggregation

## Limitations

- Only covers **newly added** dependencies in the PR diff. Dependencies already present in the
  base branch are not re-evaluated on each PR (use the Snyk nightly scan for ongoing monitoring).
- Requires the repository to have the **Dependency graph** feature enabled (GitHub setting).
- Fork PRs: the action runs but may not be able to post a PR comment (token lacks
  `pull-requests: write`); a warning annotation is emitted instead.

## File structure

```
.github/actions/dependency-vuln-check/
├── action.yml   # Composite action — thin shell wrapper that invokes check.py
├── check.py     # Vulnerability check logic (~290 lines, Python stdlib)
└── README.md    # This file

.github/
└── dependency-review-config.json   # Exception allow-list (CODEOWNERS-protected)
```

## Related

- Issue: [#29729 — Verify that a PR does not introduce a known vulnerability](https://github.com/camunda/camunda/issues/29729)
- Snyk nightly scan (ongoing monitoring): `.github/workflows/zeebe-snyk.yml`
- Frontend SBOM Snyk scan: `.github/actions/frontend-sbom-snyk/`
