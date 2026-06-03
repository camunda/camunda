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

The `ls` output from above is the authoritative list of available templates. The table below is a
default mapping for the common cases — if a template appears in the filesystem but not here, read
its `labels:` field to infer the `kind/` label.

| User intent                    | Template file              | `kind/` label          |
|--------------------------------|----------------------------|------------------------|
| Bug, defect, crash, regression | `1. bug_report.yml`        | `kind/bug`             |
| Feature, improvement           | `2. feature_request.yml`   | `kind/feature-request` |
| Task, activity, chore          | `3. task.yml`              | `kind/task`            |
| Epic breakdown                 | `4. epic breakdown.yml`    | `kind/epic`            |
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

Build the Markdown body directly from the template read in Step 2, mirroring exactly what the
GitHub form UI produces. For each item in the template's `body:` list:

- Skip `type: markdown` items (display-only).
- For `type: textarea` items: render `attributes.label` as a `### Heading` and the user's value as
  the content.
- For `type: dropdown` items: render `attributes.label` as a `### Heading` and the selected
  option's text verbatim as the content (e.g. `<!-- Zeebe- -->`). These HTML comment markers are
  what `.github/opened_issue_labeler.yml` scans on issue open to auto-apply labels.

Append a **Links** section at the end with the parent issue URL if one was provided.

Do not invent sections or use any structure other than what the template defines.

### Step 6 — Propose summary and ask for approval

Print a clear summary and wait for explicit confirmation. Do NOT call `gh issue create` yet.

```
📋 Issue summary
────────────────────────────────────────────
Title:   <proposed title>
Type:    <bug | feature | task | tech-debt | CVE>
Labels:  kind/<type>  (component/*, severity/*, likelihood/* applied automatically via labeler)
Parent:  https://github.com/camunda/camunda/issues/<N>  (or "none")

Body:
<full proposed body>
────────────────────────────────────────────
Create this issue? (y/N)
```

If the user requests changes, revise the summary and ask again before proceeding.

### Step 7 — Create the issue and register the parent relationship

After the user confirms, create the issue. Pass only the `kind/<type>` label explicitly — all
other labels (`component/*`, `severity/*`, `likelihood/*`, `affects/*`) are applied automatically
by `.github/opened_issue_labeler.yml`, which scans the body for the HTML comment markers rendered
in Step 5.

```bash
body_file=$(mktemp)
printf '%s' "<body>" > "$body_file"

gh issue create \
  --repo camunda/camunda \
  --title "<title>" \
  --body-file "$body_file" \
  --label "kind/<type>"

rm -f "$body_file"
```

Capture the new issue number from the returned URL. Then fetch its database ID (required by the
sub-issues API — the issue number alone will return 404):

```bash
gh api repos/camunda/camunda/issues/<new-issue-number> --jq '.id'
```

If a parent was provided, register the native GitHub sub-issue relationship so the new issue
appears as a child of the parent in the GitHub UI:

```bash
gh api \
  --method POST \
  repos/camunda/camunda/issues/<parent-number>/sub_issues \
  --input - <<< "{\"sub_issue_id\": <new-issue-database-id>}"
```

Print the URL of the created issue.
