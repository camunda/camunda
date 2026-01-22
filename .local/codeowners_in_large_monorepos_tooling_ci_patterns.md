# Maintaining and Leveraging `CODEOWNERS` in Large Monorepos

This document summarizes **tools, workflows, and best practices** for maintaining GitHub `CODEOWNERS` files in large organizations and **leveraging ownership metadata** for code review enforcement and CI automation (e.g. jobs per owner/team).

---

## Goals & Non‑Goals

### Goals

- Keep `CODEOWNERS` accurate and maintainable at scale
- Reduce central bottlenecks in ownership management
- Enforce meaningful review policies
- Reuse ownership metadata to optimize CI (tests per module / team)

### Non‑Goals

- Replace GitHub entirely (focus is GitHub‑native or GitHub‑compatible tooling)
- Fully prescribe org‑specific team structures

---

## 1. Maintaining `CODEOWNERS`

### 1.1 Modular Ownership Definitions

**Problem:** A single, giant `CODEOWNERS` file does not scale well.

**Pattern:** Define ownership close to the code and generate the final `CODEOWNERS` automatically.

#### Tools

- **codeowners-generator**  
  Collects ownership fragments (or metadata like `package.json` maintainers) and generates a single `CODEOWNERS` file.
  - https://www.npmjs.com/package/codeowners-generator
- **Codeowners Bot (GitHub Action)**  
  Merges multiple ownership files scattered across the repo into a valid root `CODEOWNERS` file.
  - https://github.com/marketplace/actions/codeowners-bot

**Best Practices**
- Keep ownership definitions near the module root
- Generate `CODEOWNERS` in CI and fail if it changes unexpectedly
- Prefer GitHub teams over individuals

---

### 1.2 Automatic Ownership Discovery

**Problem:** Ownership files become stale as teams evolve.

**Pattern:** Infer owners from real commit history and update automatically.

#### Tools

- **update-codeowners (GitHub Action)**  
  Uses commit history (git fame) to suggest or update owners.
  - https://github.com/marketplace/actions/update-codeowners

**Best Practices**
- Use inferred ownership as a *signal*, not the sole source of truth
- Require human approval for generated updates

---

### 1.3 Validation & Quality Gates

**Problem:** Broken patterns silently disable ownership enforcement.

#### Tools

- **codeowners-validator**  
  Validates syntax, unmatched patterns, and GitHub compatibility.
  - https://github.com/mszostok/codeowners-validator

**Best Practices**
- Run validation on every PR touching `CODEOWNERS`
- Fail CI on invalid patterns

---

## 2. Leveraging `CODEOWNERS` for Reviews

### 2.1 Built‑in GitHub Enforcement

GitHub supports:
- Automatic reviewer assignment
- “Require review from Code Owners”

**Limitation:** Only *one* matching owner approval is required.

---

### 2.2 Stronger Review Semantics

**Problem:** Critical areas need stricter guarantees.

#### Tools

- **Codeowners Multi‑Approval Check**  
  Requires *all* matching owners to approve.
  - https://github.com/marketplace/actions/codeowners-multi-approval-check
- **Reviewer Check Action**  
  Enforces a minimum number of approvals per team/owner.
  - https://github.com/marketplace/actions/reviewer-check

**Best Practices**
- Combine with branch protection rules
- Use stricter rules only for critical paths

---

### 2.3 Alternative: Rule‑Based Approval Engines

If `CODEOWNERS` becomes too limited:

- **PullApprove**  
  Replaces `CODEOWNERS` with declarative approval rules (`CODEREVIEW.toml`).
  - https://www.pullapprove.com/

**Trade‑off:** More expressive, but introduces a non‑native dependency.

---

## 3. Using `CODEOWNERS` in CI Pipelines

### 3.1 Why Owner‑Aware CI?

- Avoid running the full test suite on every change
- Route failures to accountable teams
- Enable per‑module or per‑team SLAs

---

### 3.2 Mapping Files → Owners

GitHub does not expose owner mappings directly in workflows.

#### Tools & Libraries

- **git-codeowners (Python)**  
  Query owners for specific files based on `CODEOWNERS` patterns.
  - https://pypi.org/project/git-codeowners/
- **Custom Scripts**  
  Most orgs eventually maintain a small Node/Python utility to:
  1. Read changed files in a PR
  2. Match against `CODEOWNERS`
  3. Emit a unique list of owners/teams

---

### 3.3 Pattern: Dynamic CI Jobs per Owner

```yaml
name: Owner-Aware CI
on: [pull_request]

jobs:
  resolve-owners:
    runs-on: ubuntu-latest
    outputs:
      owners: ${{ steps.owners.outputs.json }}
    steps:
      - uses: actions/checkout@v4
      - id: owners
        run: |
          python scripts/get_owners.py > owners.json
          echo "json=$(cat owners.json)" >> $GITHUB_OUTPUT

  test-per-owner:
    needs: resolve-owners
    strategy:
      matrix:
        owner: ${{ fromJson(needs.resolve-owners.outputs.owners) }}
    runs-on: ubuntu-latest
    steps:
      - name: Run tests for ${{ matrix.owner }}
        run: ./ci/run_tests.sh "${{ matrix.owner }}"
```

**Notes**
- Owners are often GitHub teams → map teams to test scopes
- Cache aggressively to keep fan‑out affordable

---

## 4. Organizational Best Practices

### Ownership Modeling

- Use **teams**, not individuals
- Avoid overlapping ownership unless intentional
- Define a fallback owner (e.g. platform team)

### Governance

- Treat `CODEOWNERS` as a **contract**, not documentation
- Require reviews for ownership changes
- Audit ownership quarterly

### CI Strategy

- Start with ownership‑aware *filtering*, not full job fan‑out
- Combine ownership with path‑based test selection
- Keep CI explainable (logs should say *why* a job ran)

---

## 5. Recommended Baseline Setup

**Minimal, high‑ROI stack:**

1. Modular ownership + generator (or bot)
2. `codeowners-validator` in CI
3. GitHub “Require Code Owner Review”
4. Lightweight script to map PR files → owners

From there, incrementally add:
- Multi‑approval checks
- Owner‑aware CI fan‑out
- Alternative approval engines

---

## Appendix: When *Not* to Use `CODEOWNERS`

- Very small repos with stable teams
- Highly experimental code where ownership churn is high

In those cases, lightweight reviewer rotation may be cheaper.

---

**Outcome:** Treat `CODEOWNERS` as a *first‑class metadata layer* — not just a review convenience — and it can become a foundation for scalable governance and CI optimization in large monorepos.

