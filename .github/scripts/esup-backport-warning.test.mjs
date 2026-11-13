import {test} from 'node:test';
import assert from 'node:assert/strict';

import {
  HIDDEN_MARKER,
  annotateSummary,
  esupBranchesFromLabels,
  findSummaryComment,
  formatBranches,
  warningMessage,
} from './esup-backport-warning.mjs';

const ESUP_DESCRIPTION = 'ON ESUP: HIGH/CRITICAL CVE, customer-requested & CI/release fixes (backport to stable/8.7)';

// Shape of backport-action's summary comment (comment_style: summary), as on
// https://github.com/camunda/camunda/pull/69098#issuecomment-6826535907
const summary = (rows, tail = '') =>
  [
    '[Backport-action](https://github.com/korthout/backport-action) backported this pull request in [workflow run 43238734701](https://github.com/camunda/camunda/actions/runs/43238734701).',
    '',
    '| Target | Status |',
    '|--------|--------|',
    ...rows,
  ].join('\n') + tail;

test('esupBranchesFromLabels keeps only backport labels whose description says ESUP', () => {
  const labels = [
    {name: 'backport stable/8.7', description: ESUP_DESCRIPTION},
    {name: 'backport stable/8.6', description: ESUP_DESCRIPTION.replace('8.7', '8.6')},
    {name: 'backport stable/8.8', description: 'Backport a pull request to stable/8.8'},
    {name: 'backport stable/8.5', description: "OUTDATED, DON'T USE (Backport a pull request to stable/8.5)"},
    {name: 'component/build-pipeline', description: 'ESUP'},
    {name: 'backport stable/8.9', description: null},
  ];
  assert.deepEqual(esupBranchesFromLabels(labels), ['stable/8.6', 'stable/8.7']);
  assert.deepEqual(esupBranchesFromLabels(undefined), []);
});

test('formatBranches joins one, two and three branches', () => {
  assert.equal(formatBranches(['stable/8.7']), '`stable/8.7`');
  assert.equal(formatBranches(['stable/8.6', 'stable/8.7']), '`stable/8.6` and `stable/8.7`');
  assert.equal(formatBranches(['a', 'b', 'c']), '`a`, `b` and `c`');
});

test('warningMessage uses the approved text for each point', () => {
  const policy =
    'fixes for exploitable High/Critical vulnerabilities, CI or release-process fixes, or fixes a customer asked for through support';
  assert.equal(
    warningMessage('label', ['stable/8.7']),
    `⚠️ \`stable/8.7\` is in Extended Support (ESUP). Backport only ${policy}. If this change is none of those, remove the \`backport stable/8.7\` label.`,
  );
  assert.equal(
    warningMessage('pull-request', ['stable/8.7']),
    `⚠️ \`stable/8.7\` is in Extended Support (ESUP). Only ${policy} belong on this branch. If this change is none of those, reconsider whether it belongs on this branch.`,
  );
  assert.equal(
    warningMessage('backport-summary', ['stable/8.7']),
    `⚠️ \`stable/8.7\` is in Extended Support (ESUP). Backport only ${policy}. If this change is none of those, close its backport PR.`,
  );
  assert.equal(
    warningMessage('backport-summary', ['stable/8.6', 'stable/8.7']),
    `⚠️ \`stable/8.6\` and \`stable/8.7\` are in Extended Support (ESUP). Backport only ${policy}. If this change is none of those, close their backport PRs.`,
  );
  assert.throws(() => warningMessage('nope', ['stable/8.7']));
});

test('warning text carries no issue reference that PR parsers could pick up', () => {
  for (const mode of ['label', 'pull-request', 'backport-summary']) {
    assert.doesNotMatch(warningMessage(mode, ['stable/8.7']), /#\d/);
  }
});

test('findSummaryComment picks the newest bot comment linking this run', () => {
  const run = '[workflow run 42](https://github.com/camunda/camunda/actions/runs/42)';
  const comments = [
    {id: 1, user: {type: 'Bot'}, body: `old ${run}`},
    {id: 3, user: {type: 'Bot'}, body: `rerun ${run}`},
    {id: 4, user: {type: 'User'}, body: `quoted ${run}`},
    {id: 5, user: {type: 'Bot'}, body: '[workflow run 420](https://github.com/camunda/camunda/actions/runs/420)'},
  ];
  assert.equal(findSummaryComment(comments, '42').id, 3);
  assert.equal(findSummaryComment(comments, '7'), undefined);
  assert.equal(findSummaryComment(undefined, '42'), undefined);
});

test('annotateSummary marks only the ESUP row and appends the warning block', () => {
  const body = summary([
    '| `stable/8.7` | :white_check_mark: Created #69194 |',
    '| `stable/8.8` | :white_check_mark: Created #69195 |',
  ]);
  const annotated = annotateSummary(body, ['stable/8.7']);
  assert.match(annotated, /^\| `stable\/8\.7` \| :white_check_mark: Created #69194 · ⚠️ ESUP \|$/m);
  assert.match(annotated, /^\| `stable\/8\.8` \| :white_check_mark: Created #69195 \|$/m);
  assert.ok(annotated.startsWith(body.split('\n| `stable/8.7`')[0]));
  assert.ok(
    annotated.endsWith(`\n\n> [!WARNING]\n> ${warningMessage('backport-summary', ['stable/8.7'])}\n${HIDDEN_MARKER}`),
  );
});

test('annotateSummary handles drafted, failed and skipped-commit rows', () => {
  const body = summary(
    [
      '| `stable/8.6` | :warning: Drafted with conflicts #69190 — 1 commit skipped (target already contains changes) |',
      '| `stable/8.7` | :x: Failed |',
      '| `stable/8.8` | :white_check_mark: Created #69195 |',
    ],
    '\n\n<details><summary>:x: stable/8.7 — unable to cherry-pick</summary>\n\nlog\n</details>',
  );
  const annotated = annotateSummary(body, ['stable/8.6', 'stable/8.7']);
  assert.match(
    annotated,
    /^\| `stable\/8\.6` \| :warning: Drafted with conflicts #69190 — 1 commit skipped \(target already contains changes\) · ⚠️ ESUP \|$/m,
  );
  assert.match(annotated, /^\| `stable\/8\.7` \| :x: Failed · ⚠️ ESUP \|$/m);
  assert.match(annotated, /<\/details>\n\n> \[!WARNING\]\n> ⚠️ `stable\/8\.6` and `stable\/8\.7` are in/);
});

test('annotateSummary does not confuse similar branch names', () => {
  const body = summary(['| `stable/8.70` | :white_check_mark: Created #1 |', '| `stable/8.7` | :x: Failed |']);
  const annotated = annotateSummary(body, ['stable/8.7']);
  assert.match(annotated, /^\| `stable\/8\.70` \| :white_check_mark: Created #1 \|$/m);
  assert.match(annotated, /^\| `stable\/8\.7` \| :x: Failed · ⚠️ ESUP \|$/m);
});

test('annotateSummary is idempotent on an already annotated body', () => {
  const once = annotateSummary(summary(['| `stable/8.7` | :x: Failed |']), ['stable/8.7']);
  assert.equal(annotateSummary(once, ['stable/8.7']), once);
});

test('annotateSummary returns null when a row is missing, so the caller falls back', () => {
  // backport-action's pre-loop failure: no table at all.
  const noTable =
    '[Backport-action](https://github.com/korthout/backport-action) failed to backport this pull request in [workflow run 1](u).\n\nThe pull request is not merged';
  assert.equal(annotateSummary(noTable, ['stable/8.7']), null);
  // A future format change in the row layout.
  assert.equal(annotateSummary(summary(['| stable/8.7 | Created #1 |']), ['stable/8.7']), null);
});
