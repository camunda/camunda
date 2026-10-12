import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  DIVERGENCE_LIST_CAP,
  parseGeneratorFiles,
  planApply,
  renderDegradedReport,
  renderReport,
  type ApplyPlan,
  type GeneratorFiles,
  type Observed,
} from '../src/apply';

const VERSION = '8.8.31';

function files(overrides: Partial<GeneratorFiles> = {}): GeneratorFiles {
  return {
    labels: { schemaVersion: '2.0.0', version: VERSION, issues: [1, 2, 3, 4], pullRequests: [101, 102, 103] },
    comments: {
      schemaVersion: '2.0.0',
      version: VERSION,
      entries: [
        entry(1, [101], 'closing'),
        entry(2, [102], 'contributor'),
        entry(3, [103, 104, 105, 106, 107, 108, 109, 110, 111], 'closing'),
        entry(4, [101], 'closing'),
      ],
    },
    changelog: [
      { number: 101, attributionSource: 'section', deliveryPath: 'direct' },
      { number: 102, attributionSource: 'closingIssuesReferences', deliveryPath: 'backport' },
    ],
    ...overrides,
  };
}

function entry(issueNumber: number, prNumbers: number[], relationKind: 'closing' | 'contributor') {
  return {
    issueNumber,
    prNumbers,
    relationKind,
    text: relationKind === 'closing' ? `Released in ${VERSION} (#${prNumbers[0]}).` : `Partially delivered in ${VERSION} by #${prNumbers[0]}.`,
    marker: `<!-- release-notes:${VERSION}:issue-${issueNumber} -->`,
  };
}

function observed(overrides: Partial<Observed> = {}): Observed {
  return {
    zclIssues: new Set([1, 2, 9]),
    zclPullRequests: new Set([101]),
    issueFacts: new Map([
      [1, { closed: true, stateReason: 'COMPLETED' }],
      [2, { closed: true, stateReason: 'COMPLETED' }],
      [3, { closed: false, stateReason: null }],
      [4, { closed: true, stateReason: 'NOT_PLANNED' }],
      [9, { closed: true, stateReason: 'COMPLETED' }],
    ]),
    ...overrides,
  };
}

test('splits issues into both, generator-only and zcl-only', () => {
  // given the generator plans 1-4 and zcl labelled 1, 2 and 9
  // when
  const plan = planApply(files(), observed());

  // then
  assert.deepEqual(plan.issues, { both: [1, 2], generatorOnly: [3, 4], zclOnly: [9] });
});

test('reports pull requests as counts only', () => {
  const plan = planApply(files(), observed({ zclPullRequests: new Set([101, 500]) }));

  assert.deepEqual(plan.pullRequests, { generator: 3, zcl: 2, both: 1, generatorOnly: 2, zclOnly: 1 });
});

test('labels only what zcl has not already labelled', () => {
  const plan = planApply(files(), observed());

  const labelled = plan.operations.filter((operation) => operation.type === 'add-label');
  assert.deepEqual(
    labelled.map((operation) => `${operation.target}#${operation.number}`),
    ['issue#3', 'issue#4', 'pull_request#102', 'pull_request#103'],
  );
});

test('comments only on closed issues and names why the others are skipped', () => {
  const plan = planApply(files(), observed());

  const comments = plan.operations.filter((operation) => operation.type === 'create-comment');
  assert.deepEqual(comments.map((operation) => operation.issueNumber), [1, 2]);
  const skipped = plan.operations.flatMap((operation) => (operation.type === 'skip' && operation.what === 'comment' ? [operation] : []));
  assert.deepEqual(
    skipped.map((operation) => [operation.number, operation.reason]),
    [
      [3, 'issue still open'],
      [4, 'closed as not planned'],
    ],
  );
});

test('an issue whose state could not be read is skipped, never commented on', () => {
  const plan = planApply(files(), observed({ issueFacts: new Map() }));

  assert.equal(plan.operations.filter((operation) => operation.type === 'create-comment').length, 0);
  assert.ok(plan.operations.some((operation) => operation.type === 'skip' && operation.reason === 'issue state unknown'));
});

test('an empty zcl baseline is flagged and the whole generator plan becomes label operations', () => {
  const plan = planApply(files(), observed({ zclIssues: new Set(), zclPullRequests: new Set() }));

  assert.equal(plan.baselineEmpty, true);
  assert.equal(plan.operations.filter((operation) => operation.type === 'add-label').length, 7);
});

test('hints state facts about the delivering PR and the issue', () => {
  const plan = planApply(files(), observed());

  assert.match(plan.hints[4]!, /via PR #101 \(closing; attribution section; delivery direct\)/);
  assert.match(plan.hints[4]!, /closed as not planned/);
  assert.match(plan.hints[9]!, /no PR in the generator's range is attributed to it/);
  assert.match(plan.hints[9]!, /closed/);
});

test('the report lists divergences and what would be done', () => {
  const markdown = renderReport(planApply(files(), observed()));

  assert.match(markdown, /Release-notes apply \(dry run\): 8\.8\.31/);
  assert.match(markdown, /Nothing was written to GitHub/);
  assert.match(markdown, /\| Both agree \| 2 \|/);
  assert.match(markdown, /Generator only \(zcl missed\) \| 2 \|/);
  assert.match(markdown, /#### Generator only \(2\)/);
  assert.match(markdown, /#### zcl only \(1\)/);
  assert.match(markdown, /Label 2 issues and 2 pull requests/);
  assert.match(markdown, /Comment on 2 issues: 1 "Released in" and 1 "Partially delivered"/);
  assert.match(markdown, /Skipped: 1 still open, 1 closed as not planned/);
});

test('the example comment shows its hidden marker inside a code fence', () => {
  const markdown = renderReport(planApply(files(), observed()));

  assert.match(markdown, /<details><summary>Show one comment as it would be posted<\/summary>/);
  assert.ok(markdown.includes('```text\nReleased in 8.8.31 (#101).\n<!-- release-notes:8.8.31:issue-1 -->\n```'));
});

test('lists are capped and the overflow is counted', () => {
  const many = Array.from({ length: DIVERGENCE_LIST_CAP + 7 }, (_, index) => 1000 + index);
  const plan = planApply(
    files({
      labels: { schemaVersion: '2.0.0', version: VERSION, issues: many, pullRequests: [] },
      comments: { schemaVersion: '2.0.0', version: VERSION, entries: [] },
    }),
    observed({ zclIssues: new Set([1]), issueFacts: new Map() }),
  );

  const markdown = renderReport(plan);

  assert.equal(plan.issues.generatorOnly.length, DIVERGENCE_LIST_CAP + 7);
  assert.match(markdown, /7 more: see apply-divergence\.json/);
  assert.ok(!markdown.includes('#1020 '), 'only the first 20 are listed');
});

test('a clean comparison says there is no divergence', () => {
  const plan = planApply(
    files({
      labels: { schemaVersion: '2.0.0', version: VERSION, issues: [1], pullRequests: [101] },
      comments: { schemaVersion: '2.0.0', version: VERSION, entries: [entry(1, [101], 'closing')] },
    }),
    observed({ zclIssues: new Set([1]), zclPullRequests: new Set([101]) }),
  );

  assert.match(renderReport(plan), /No divergence/);
});

test('an empty baseline renders a warning instead of a comparison', () => {
  const plan = planApply(files(), observed({ zclIssues: new Set(), zclPullRequests: new Set() }));

  const markdown = renderReport(plan);

  assert.match(markdown, /No issues carry `version:8\.8\.31`, so there is nothing to compare with/);
  assert.match(markdown, /would label 4 issues and 3 PRs/);
  assert.ok(!markdown.includes('| Both agree'), 'no comparison table when nothing was compared');
});

test('markup in interpolated text is escaped', () => {
  const hostile = files({ labels: { schemaVersion: '2.0.0', version: '<img src=x>', issues: [], pullRequests: [] } });

  const markdown = renderReport(planApply(hostile, observed()));

  assert.ok(!markdown.includes('<img'));
  assert.match(markdown, /&lt;img src=x&gt;/);
});

test('a degraded report names the reason and never reads as a comparison', () => {
  const markdown = renderDegradedReport('labels.json not found. The generate step outcome was failure.');

  assert.match(markdown, /Skipped: labels\.json not found/);
  assert.ok(!markdown.includes('Both agree'));
});

test('parseGeneratorFiles refuses an unknown schema major', () => {
  const raw = {
    labels: JSON.stringify({ schemaVersion: '3.0.0', version: VERSION, issues: [], pullRequests: [] }),
    comments: JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, entries: [] }),
  };

  assert.throws(() => parseGeneratorFiles(raw), /schemaVersion 3\.0\.0.* supports 2\.x/);
});

test('parseGeneratorFiles refuses mismatching versions and malformed JSON', () => {
  const labels = JSON.stringify({ schemaVersion: '2.0.0', version: '8.8.1', issues: [], pullRequests: [] });
  const comments = JSON.stringify({ schemaVersion: '2.0.0', version: '8.8.2', entries: [] });

  assert.throws(() => parseGeneratorFiles({ labels, comments }), /different versions/);
  assert.throws(() => parseGeneratorFiles({ labels: '{nope', comments }), /labels\.json is not valid JSON/);
});

test('parseGeneratorFiles reads changelog.json when present and tolerates its absence', () => {
  const labels = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, issues: [], pullRequests: [] });
  const comments = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, entries: [] });
  const changelog = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, prs: [{ number: 7, attributionSource: 'section', deliveryPath: 'direct', title: 'x' }] });

  assert.deepEqual(parseGeneratorFiles({ labels, comments, changelog }).changelog, [
    { number: 7, attributionSource: 'section', deliveryPath: 'direct' },
  ]);
  assert.deepEqual(parseGeneratorFiles({ labels, comments }).changelog, []);
});

test('the plan is plain data a JSON artifact can carry', () => {
  const plan: ApplyPlan = planApply(files(), observed());

  assert.doesNotThrow(() => JSON.stringify(plan));
});
