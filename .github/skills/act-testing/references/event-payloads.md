# Event Payload Fixtures

Canonical payloads live under `references/event-payloads/*.json`.
Use these deterministic fixtures with your `act` command on prepared test harness workflows.

## Core fixtures

- `push-release-branch.json`
- `push-non-release-branch.json`
- `push-release-branch-bot.json`
- `push-main.json`
- `pr-labeled-backport.json`
- `pr-opened-ready.json`
- `pr-opened-draft.json`
- `pr-closed-merged.json`
- `workflow-dispatch-dry-run.json`
- `empty-call.json`

## Usage example

```bash
act push -e .github/skills/act-testing/references/event-payloads/push-release-branch.json \
  -W .github/workflows/test-release-branch-notifications.yml --secret-file .secrets --reuse
```

## Notes

- Prefer event-centric fixtures over workflow-specific fixture names.
- Add workflow-specific payloads only when generic fixtures are not enough.

