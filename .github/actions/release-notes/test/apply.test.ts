import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  DIVERGENCE_LIST_CAP,
  parseDryRun,
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

function state(closed: boolean, stateReason: string | null, comments: string[] = []) {
  return { closed, stateReason, comments };
}

const LEGACY_COMMENT = `This has been released in version [${VERSION}](https://github.com/camunda/camunda/releases/tag/${VERSION}). See release notes [here](https://github.com/camunda/camunda/releases/tag/${VERSION}) for details.`;

function observed(overrides: Partial<Observed> = {}): Observed {
  return {
    zclIssues: new Set([1, 2, 9]),
    zclPullRequests: new Set([101]),
    issueFacts: new Map([
      [1, state(true, 'COMPLETED')],
      [2, state(true, 'COMPLETED')],
      [3, state(false, null)],
      [4, state(true, 'NOT_PLANNED')],
      [9, state(true, 'COMPLETED')],
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

  assert.match(plan.hints[4]!, /closing, via PR #101 \(attribution section; delivery direct\)/);
  assert.match(plan.hints[4]!, /closed as not planned/);
  assert.match(plan.hints[9]!, /the generator's labels\.json does not list it/);
  assert.match(plan.hints[9]!, /closed/);
});

test('a hint quotes each delivering PR with its own attribution and delivery path', () => {
  const plan = planApply(files(), observed());

  assert.equal(plan.hints[3], 'closing, via PR #103, #104, #105, #106, #107, #108, #109, #110, #111 · issue open');
  const both = files({ comments: { schemaVersion: '2.0.0', version: VERSION, entries: [entry(4, [101, 102], 'closing')] } });
  assert.match(
    planApply(both, observed()).hints[4]!,
    /via PR #101 \(attribution section; delivery direct\), #102 \(attribution closingIssuesReferences; delivery backport\)/,
  );
});

test('a generator-only issue without a comment entry says so instead of guessing', () => {
  const plan = planApply(files({ comments: { schemaVersion: '2.0.0', version: VERSION, entries: [] } }), observed());

  assert.match(plan.hints[3]!, /the generator has no comment entry for it/);
});

test('an issue the legacy script already commented on is not commented again', () => {
  const facts = new Map(observed().issueFacts);
  facts.set(1, state(true, 'COMPLETED', ['unrelated', LEGACY_COMMENT]));

  const plan = planApply(files(), observed({ issueFacts: facts }));

  assert.deepEqual(plan.operations.filter((operation) => operation.type === 'create-comment').map((operation) => operation.issueNumber), [2]);
  assert.ok(plan.operations.some((operation) => operation.type === 'skip' && operation.number === 1 && operation.reason === 'already commented by the legacy script'));
});

test('an issue that already carries the generator marker is not commented again', () => {
  const facts = new Map(observed().issueFacts);
  facts.set(2, state(true, 'COMPLETED', [`Partially delivered in ${VERSION} by #102.\n<!-- release-notes:${VERSION}:issue-2 -->`]));

  const plan = planApply(files(), observed({ issueFacts: facts }));

  assert.ok(plan.operations.some((operation) => operation.type === 'skip' && operation.number === 2 && operation.reason === 'already commented by the generator'));
});

test('compares the comments the generator would post with the ones the legacy script posted', () => {
  // given the legacy script commented on 1 (generator agrees) and 9 (generator plans nothing)
  const facts = new Map(observed().issueFacts);
  facts.set(1, state(true, 'COMPLETED', [LEGACY_COMMENT]));
  facts.set(9, state(true, 'COMPLETED', [LEGACY_COMMENT]));

  // when
  const plan = planApply(files(), observed({ issueFacts: facts }));

  // then
  assert.deepEqual(plan.comments, { both: [1], generatorOnly: [2], legacyOnly: [9] });
  assert.match(plan.hints[9]!, /the generator's labels\.json does not list it/);
  const markdown = renderReport(plan);
  assert.match(markdown, /Legacy script commented, generator agrees \| 1 \|/);
  assert.match(markdown, /Legacy commented, generator would not \| 1 \|/);
  assert.match(markdown, /#### Legacy commented, generator would not \(1\)/);
});

test('the report says when the legacy script has not commented yet', () => {
  const markdown = renderReport(planApply(files(), observed()));

  assert.match(markdown, /Generator would comment, legacy has not \| 2 \|/);
  assert.match(markdown, /legacy script runs weekly/);
});

test('a repeated comment entry for one issue plans one comment', () => {
  const doubled = files({
    comments: { schemaVersion: '2.0.0', version: VERSION, entries: [entry(1, [101], 'closing'), entry(1, [102], 'contributor')] },
  });

  const plan = planApply(doubled, observed());

  assert.equal(plan.operations.filter((operation) => operation.type === 'create-comment' && operation.issueNumber === 1).length, 1);
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
  assert.match(markdown, /Skipped issues: 1 still open, 1 closed as not planned/);
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

  assert.match(markdown, /### Release-notes apply \(dry run\): &lt;img src=x&gt;/);
  assert.ok(markdown.includes('`version:<img src=x>`'), 'a code span shows markup literally, so it is not escaped there');
  assert.ok(!markdown.replaceAll('`version:<img src=x>`', '').includes('<img'));
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

test('parseGeneratorFiles ignores an unusable changelog.json instead of failing the comparison', () => {
  const labels = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, issues: [], pullRequests: [] });
  const comments = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, entries: [] });

  for (const changelog of ['{}', '{"prs": 3}', '{nope']) {
    assert.deepEqual(parseGeneratorFiles({ labels, comments, changelog }).changelog, [], changelog);
  }
});

test('parseGeneratorFiles names a labels.json or comments.json of the wrong shape', () => {
  const labels = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, issues: [], pullRequests: [] });
  const comments = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, entries: [] });
  const noIssues = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, pullRequests: [] });
  const badEntries = JSON.stringify({ schemaVersion: '2.0.0', version: VERSION, entries: [{ issueNumber: 'x' }] });

  assert.throws(() => parseGeneratorFiles({ labels: noIssues, comments }), /labels\.json must hold/);
  assert.throws(() => parseGeneratorFiles({ labels, comments: badEntries }), /comments\.json must hold/);
});

test('dry-run accepts only true or false', () => {
  assert.equal(parseDryRun('true'), true);
  assert.equal(parseDryRun(''), true);
  assert.equal(parseDryRun('false'), false);
  for (const value of ['False', 'TRUE', '0', 'no']) assert.throws(() => parseDryRun(value), /must be "true" or "false"/, value);
});

test('the plan is plain data a JSON artifact can carry', () => {
  const plan: ApplyPlan = planApply(files(), observed());

  assert.doesNotThrow(() => JSON.stringify(plan));
});
