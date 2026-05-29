---
name: create-issue
description: Create a GitHub issue in camunda/camunda with the correct template, component label, and parent link. Use when asked to create, file, or open an issue — for bugs, features, tasks, tech debt, or CVEs.
---

# Create Issue

Creates a well-formed GitHub issue in `camunda/camunda` following the official templates. Prevents
missing parent links, wrong component labels, and skipped required fields.

## Prerequisites

```bash
gh auth status  # must succeed
```

## Procedure

### Step 1 — Read available templates

At invocation time, list the templates from the filesystem:

```bash
ls .github/ISSUE_TEMPLATE/*.yml
```

Match the user's request to one of these types:

| User intent                    | Template file              | `kind/` label          |
|-------------------------------|----------------------------|------------------------|
| Bug, defect, crash, regression | `1. bug_report.yml`        | `kind/bug`             |
| Feature, improvement           | `2. feature_request.yml`   | `kind/feature-request` |
| Task, activity, chore          | `3. task.yml`              | `kind/task`            |
| CVE, security vulnerability    | `5. CVE.yml`               | `kind/cve`             |
| Tech debt, cleanup             | `6. tech debt.yml`         | `kind/tech-debt`       |

If the issue type is ambiguous, ask the user to pick one before proceeding.

### Step 2 — Read the template and collect field values

Read the chosen template:

```bash
cat ".github/ISSUE_TEMPLATE/<chosen>.yml"
```

Ask the user for a free-form description of the issue. Use that description to infer values for
every field in the template's `body:` list. For each item:
- Skip `type: markdown` items (they are display-only).
- Infer a value from the description where possible — including title, dropdown selections, and
  prose sections.
- Only ask the user a follow-up question when the description does not provide enough signal to
  fill a field confidently. Batch all uncertain fields into a single follow-up rather than asking
  one at a time.
- If `validations.required: true` and a value cannot be inferred, the user must provide one before
  proceeding.
- If `validations.required: false` or absent and nothing can be inferred, leave the field empty.

### Step 3 — Infer the component label

Detect which module the issue relates to from the current working directory and modified files:

```bash
git diff --name-only HEAD
```

Map path prefixes to component labels:

| Path prefix              | Component label                  |
|--------------------------|----------------------------------|
| `zeebe/`                 | `component/zeebe`                |
| `operate/`               | `component/operate`              |
| `tasklist/`              | `component/tasklist`             |
| `identity/`              | `component/identity`             |
| `optimize/`              | `component/optimize`             |
| `clients/`               | `component/clients`              |
| `webapp/`                | `component/c8-api`               |
| `gateways/gateway-mcp/`  | `component/mcp`                  |
| `gateways/`              | `component/gateway`              |
| `db/` or `search/`       | `component/data-layer`           |
| `document/`              | `component/document-handling`    |
| `testing/`               | `component/camunda-process-test` |
| `qa/`                    | `component/qa`                   |
| `.github/` or `.claude/` | `component/build-pipeline`       |

When multiple modules are touched, pick the label for the most-affected one. If uncertain, ask the
user. If no files are modified, ask the user which component applies.

### Step 4 — Ask for the parent issue

Ask the user:

> "Does this issue have a parent issue or epic? If so, please provide the issue number or URL."

If they provide one, verify it exists and fetch its title for display in the summary:

```bash
gh issue view <number> --repo camunda/camunda --json number,title,url
```

If the user says there is no parent, omit the parent link from the body.

### Step 5 — Compose the issue body

Build the Markdown body directly from the template read in Step 2. Only render `type: textarea`
items — use `attributes.label` as the `### Heading` and the user's value as the content. Skip all
`type: dropdown` items from the body; those are metadata captured via labels (see Step 7).
Append a **Links** section at the end with the parent issue URL if one was provided.

Do not invent sections or use any structure other than what the template defines.

### Step 6 — Propose summary and ask for approval

Print a clear summary and wait for explicit confirmation. Do NOT call `gh issue create` yet.

```
📋 Issue summary
────────────────────────────────────────────
Title:   <proposed title>
Type:    <bug | feature | task | tech-debt | CVE>
Labels:  kind/<type>, component/<component>
Parent:  https://github.com/camunda/camunda/issues/<N>  (or "none")

Body:
<full proposed body>
────────────────────────────────────────────
Create this issue? (y/N)
```

If the user requests changes, revise the summary and ask again before proceeding.

### Step 7 — Create the issue and register the parent relationship

After the user confirms, create the issue. Apply labels for all dropdown metadata:

- `kind/<type>` — from the template (e.g. `kind/bug`)
- `component/<component>` — inferred in Step 3
- `impact/<level>` — when the user provided a severity other than Unknown (`impact/low`, `impact/medium`, `impact/high`, `impact/critical`)

```bash
gh issue create \
  --repo camunda/camunda \
  --title "<title>" \
  --body "<body>" \
  --label "kind/<type>" \
  --label "component/<component>" \
  --label "impact/<level>"   # omit if severity is Unknown
```

Capture the new issue number from the returned URL. Then, if a parent was provided, register the
native GitHub sub-issue relationship so the new issue appears as a child of the parent in the
GitHub UI:

```bash
gh api \
  --method POST \
  repos/camunda/camunda/issues/<parent-number>/sub_issues \
  -F sub_issue_id=<new-issue-number>
```

Print the URL of the created issue.
