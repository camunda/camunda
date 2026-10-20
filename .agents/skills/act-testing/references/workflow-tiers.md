# Workflow Tiers for Act Testability

Last reviewed: 2026-02-26.
Re-classify when workflow logic changes.

If temporary `test-*.yml` files are created for validation, they must be removed before merge.

## Tier 1 — Fully act-testable

- `.github/workflows/release-branch-notifications.yml`
- `.github/workflows/reject-updates-using-merge-commit.yml`
- `.github/workflows/generate-snapshot-docker-tag.yml`

## Tier 2 — Partially testable

Representative workflows for first expansion:
- `.github/workflows/create-urgent-hotfix-img.yml` (validate-inputs)
- `.github/workflows/auto-merge-back-to-stable.yml` (version and branch checks)

## Tier 3 — Not act-testable

Pure orchestration and external-only integration workflows. If selected, document why act does not provide useful signal.
