On surface 'saas-ci' there is no failing test to read: every Playwright spec
passed and the downstream run went red on a job around them (org cleanup,
report merge, an upload). Each entry in /tmp/test_specs.json is that job —
'file' is the workflow in c8-cross-component-e2e-tests that owns it, 'test_name'
is the job, 'error' names the failing step. Read the step from the run log
(gh run view <id> --repo camunda/c8-cross-component-e2e-tests --log-failed) and
fix the step itself. A transient external call gets retry-with-backoff and a
per-attempt timeout; a real defect gets the real fix. continue-on-error is never
the answer, and neither is telling the on-call to re-run.

Commit/PR type: 'test:' for test-only changes, 'ci:' for workflow changes,
'fix:' for product or chart code.

PR base: a camunda/camunda PR MUST be opened with --base ${BASE_REF}. Without it
gh targets the default branch, so a stable-branch fix would carry the whole
stable-to-main delta. PRs in the other repos take their own default branch.
