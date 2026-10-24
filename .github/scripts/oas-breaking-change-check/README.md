# OC REST breaking-change check

Detects wire-level breaking changes in the Orchestration Cluster REST API spec
(`zeebe/gateway-protocol/src/main/proto/v2/`) that a pull request introduces, using
[oasdiff](https://github.com/oasdiff/oasdiff). An n-1 request or response must keep working against
an n server; see section 2.7 of the
[REST API endpoint guidelines](../../../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes).

The check currently runs in reporting-only mode: it comments on the PR and does not fail the build on
breaking changes. The job fails when the check cannot complete (setup, git, a spec that does not
parse, an oasdiff error) or the comment cannot be posted.

## How it works

Only breaks that are **new in the PR relative to the last release** are reported:

```
new = ERR(baseline -> PR head) - ERR(baseline -> PR base)     (matched by oasdiff fingerprint)
```

- The baseline is a release tag, never a branch head. For `main` and feature branches it is
  `backwards.compat.version` from `parent/pom.xml`; for `stable/X.Y` it is the tag `X.Y.0`.
- Breaks already merged to the PR's base branch are part of `baseline -> base`, so they drop out
  without any ignore file. Changes to endpoints or fields that have not shipped yet are not reported.
- The spec entry is `proto/v2/rest-api.yaml`, or the monolithic `proto/rest-api.yaml` on
  `stable/8.8`.
- Each tree is exported with `git archive` and normalized before diffing (the checkout is never
  modified): the `headers` of multipart `encoding` entries are removed (oasdiff crashes on `$ref`'d
  ones) and custom (PascalCase) string `format`s such as `ProcessInstanceKey` are dropped (SDK
  branding, not wire).
- oasdiff runs in default mode, not `--flatten-allof`, which silently drops `nullable: true` placed
  next to `allOf`.
- Only ERR findings count, WARN is informational, with one promotion rule. oasdiff downgrades a
  default-error check to WARN (disclaimer `all-of-not-flattened`) when the change sits inside an
  `allOf`, and our spec wraps nearly every `$ref` property in a one-item `allOf`. `check.py` therefore
  counts such a finding as ERR when oasdiff's default level for its id is `error` (read once per run
  from `oasdiff checks changelog -f json`). That includes the composition checks (`*-all-of-*`,
  `*-one-of-*`, `*-any-of-*`): an added `allOf` branch can bring new constraints that oasdiff does not
  report on their own.
- `severity-levels.txt` holds our deliberate overrides of oasdiff's defaults:
  `response-property-enum-value-added warn` (section 2.7), plus `response-optional-property-removed err`
  and `response-media-type-name-specialized err`. An id listed there is never promoted, so the file
  decides its level.
- Findings are grouped by rule id and spec location, so one shared-schema change that fans out to
  hundreds of operations reads as one finding.
- If any step fails (e.g. oasdiff on `baseline → head`), the script exits non-zero and the job fails;
  the log names the failing step. A failure is never reported as "no breaks".

## Getting a break accepted

Start a discussion in
[#top-c8-cluster-api-governance](https://camunda.slack.com/archives/C0A154VV8DB). Either change the
PR to remove the break, or API governance accepts it by adding the `skip-oasdiff` label to the PR,
which skips the job. The label is read from the pull request event, so it takes effect on the next
push; re-running the job reuses the old event and does not see it.

## Known false positives

1. **Search field converted to advanced filter** (`string`/enum to `oneOf[enum, Advanced…Filter]`)
   reports `request-property-enum-value-removed`. This is wire-compatible but SDK-breaking, and is
   created by our severity override.
2. **Wrapping a property as `nullable: true` + `allOf: [$ref]`** can report
   `response-required-property-removed` / `response-optional-property-removed`. It often co-occurs with
   a real nullable change, so governance will look at it anyway.
3. **Moving a property onto a shared type** (`type: string` to `allOf: [$ref: GroupId]`, or pulling
   properties into a base schema) reports `request-property-all-of-added` and similar. Often it only
   documents limits the server already enforced, but the new branch can also add real constraints
   (e.g. `maxLength`), so it goes to governance.
4. **Fan-out**: one shared-schema change is reported once per endpoint and status code (e.g. the
   ProblemDetail `uri` to `uri-reference` change produced 600+ findings). The report groups them by
   root cause.

Spec corrections (a breaking change per the guidelines where the server already behaved that way,
e.g. making a response field nullable to match reality) are flagged on purpose and go to governance
like any other break.

## Known false negatives

- Schema renames. They are SDK-only breaks, out of scope for this check.
- Path-parameter pattern changes behind `allOf` (default mode does not see them).
- Tightening a released field again after an unreleased change moved it into an `allOf`. Example:
  the release has `groupId: {type: string}`, the base branch already wraps it in
  `allOf: [$ref: SharedId]` with `maxLength: 256`, and the PR lowers that to 255. Both release
  comparisons report the same `request-property-all-of-added` (its text names the subschema, not its
  constraints), so the PR's change is filtered out as already on the base branch. The first change
  did go to governance.

## Limitations (phase 1)

- A push that removes every spec change skips the job (the spec paths filter no longer matches), so a
  comment posted for an earlier push stays as it was.
- Once the job is skipped by the `skip-oasdiff` label, the existing comment keeps showing the last
  report.
- If the check cannot run on a later push (setup failure, check error, timeout), an existing comment
  is rewritten to say so, but no comment is created.
- Fork PRs get the job summary only: their token is read-only, so no comment is posted.

## Running locally

`check.py` is the whole tool; there are no unit tests. It runs in CI only, and is verified by running
it against real spec changes. Run it from the root of a checkout whose `HEAD` is the PR merge commit
(`HEAD^1` is the PR base):

```shell
python3 -m venv /tmp/oas-check-venv
/tmp/oas-check-venv/bin/pip install -r .github/scripts/oas-breaking-change-check/requirements.txt
/tmp/oas-check-venv/bin/python .github/scripts/oas-breaking-change-check/check.py \
  --base-ref main --oasdiff "$(which oasdiff)" --out-dir /tmp/oas-check
cat /tmp/oas-check/status /tmp/oas-check/report.md
```

`status` is one word, `none` or `breaking`, and the exit code is 0 for both. When the check cannot
complete, it exits non-zero and writes neither file.

## Upgrading oasdiff

oasdiff is pinned and checksum-verified in the workflow. Rule ids and default levels change between
releases, so an upgrade is a deliberate change. Bump the version and re-run the smoke checks before
merging a Renovate bump:

1. Run the check locally with the new binary against a PR that touches the spec.
2. Diff `8.10.0` against `main` with the old and the new binary and compare the ERR findings.

