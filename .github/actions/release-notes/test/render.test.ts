import assert from 'node:assert/strict';
import { test } from 'node:test';
import { render, SCHEMA_VERSION } from '../src/render';
import type { RenderPrInput } from '../src/render';

function pr(overrides: Partial<RenderPrInput> = {}): RenderPrInput {
  return {
    number: 1,
    title: 'feat: add batch delete API',
    section: 'Features',
    visibility: 'customer',
    component: null,
    breaking: false,
    issueNumbers: [100],
    closesIssueNumbers: [],
    attributionSource: 'section',
    ...overrides,
  };
}

test('a single closing PR appears in the customer body under its section, linking the issue', () => {
  const result = render([pr({ number: 1, issueNumbers: [100], closesIssueNumbers: [100] })], [], {
    version: '8.8.30',
    allowUnattributed: false,
  });
  assert.match(result.customerBody, /## Features/);
  assert.match(result.customerBody, /#100/);
});

test('an unattributed bucket reports a failure reason by default, but still renders every output', () => {
  // given
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];

  // when
  const result = render([], unattributed, { version: '8.8.30', allowUnattributed: false });

  // then — the failure names the pull request, and every output still exists,
  // because audit.json's whole purpose is explaining a failed run.
  assert.match(result.failureReason ?? '', /#2/);
  assert.ok(result.fullAsset.length > 0);
  assert.equal((result.auditJson as { version: string }).version, '8.8.30');
});

test('audit.json carries the run\'s audit lines, not just its overrides', () => {
  // given — the lines the job also logs as warnings
  const warnings = ['Ruleset-bypass anomaly: commit abc has no associated pull request', 'PR #9: post_gate_fallback_attribution (legacyBodyScan).'];

  // when
  const result = render([pr({ number: 9 })], [], { version: '8.9.0', allowUnattributed: false, warnings });

  // then — a log is not an artifact: nothing downstream can read, diff or
  // archive one, so the audit guarantees have to survive in the file too.
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, warnings);
});

test('audit.json carries an empty warning list rather than omitting the field', () => {
  // when — a clean run
  const result = render([pr({ number: 9 })], [], { version: '8.9.0', allowUnattributed: false });

  // then — a consumer never has to distinguish absent from empty
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, []);
});

test('a failed run records no overrides — nothing was overridden', () => {
  // given — the guard failed, so no exception was approved
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];

  // when
  const result = render([], unattributed, { version: '8.8.30', allowUnattributed: false });

  // then — writing rows here made a plain failure indistinguishable from an
  // approved exception, in the one file whose job is telling them apart.
  assert.deepEqual((result.auditJson as { overrides: unknown[] }).overrides, []);
});

test('the two failures sharing the gate bucket are named apart, each with its own remedy', () => {
  // A PR that declared nothing and one whose refs were all dead need opposite
  // fixes, so the message must not report both as merely "unattributed".
  const bucket = [
    pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] }),
    pr({ number: 3, attributionSource: 'resolutionFailed', issueNumbers: [] }),
  ];
  const reason = render([], bucket, { version: '8.8.30', allowUnattributed: false }).failureReason ?? '';

  assert.match(reason, /No issue reference found: #2/);
  assert.match(reason, /Every referenced issue was unresolvable: #3/);
  // #3 must not be described as having no reference — that is the misread.
  assert.doesNotMatch(reason, /No issue reference found: [^.]*#3/);
  assert.match(reason, /allow-unattributed=true/);
});

test('the gate message names only the failure kinds actually present', () => {
  const onlyDead = [pr({ number: 7, attributionSource: 'resolutionFailed', issueNumbers: [] })];
  const reason = render([], onlyDead, { version: '8.8.30', allowUnattributed: false }).failureReason ?? '';

  assert.match(reason, /Every referenced issue was unresolvable: #7/);
  assert.doesNotMatch(reason, /No issue reference found/);
});

test('allow-unattributed with a reason overrides the failure and records the reason in audit.json', () => {
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];
  const result = render([], unattributed, {
    version: '8.8.30',
    allowUnattributed: true,
    unattributedReason: 'known bot noise, tracked in #999',
  });
  assert.ok(
    (result.auditJson as { overrides: { number: number; reason: string }[] }).overrides.some(
      (o) => o.number === 2 && o.reason.includes('known bot noise'),
    ),
  );
});

test('allow-unattributed without a reason still fails — the reason is required, not optional', () => {
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];
  const result = render([], unattributed, { version: '8.8.30', allowUnattributed: true });
  assert.ok(result.failureReason);
});

test('all four JSON outputs carry the literal schemaVersion', () => {
  const result = render([pr()], [], { version: '8.8.30', allowUnattributed: false });
  for (const doc of [result.changelogJson, result.labelsJson, result.auditJson, result.commentsJson]) {
    assert.equal((doc as { schemaVersion: string }).schemaVersion, SCHEMA_VERSION);
  }
});

test('an issue this PR actually closes gets a "Released" comment', () => {
  const result = render([pr({ number: 10, issueNumbers: [100], closesIssueNumbers: [100] })], [], {
    version: '8.8.6',
    allowUnattributed: false,
  });
  const entries = (result.commentsJson as { entries: { issueNumber: number; relationKind: string; text: string }[] }).entries;
  const entry = entries.find((e) => e.issueNumber === 100);
  assert.equal(entry?.relationKind, 'closing');
  assert.match(entry!.text, /Released in 8\.8\.6/);
});

test('an issue this PR only contributes to (does not close) gets a "Partially delivered" comment, never "Released"', () => {
  const result = render([pr({ number: 11, issueNumbers: [100], closesIssueNumbers: [] })], [], {
    version: '8.8.5',
    allowUnattributed: false,
  });
  const entries = (result.commentsJson as { entries: { issueNumber: number; relationKind: string; text: string }[] }).entries;
  const entry = entries.find((e) => e.issueNumber === 100);
  assert.equal(entry?.relationKind, 'contributor');
  assert.match(entry!.text, /Partially delivered in 8\.8\.5 by #11/);
  assert.doesNotMatch(entry!.text, /Released/);
});

test('several pull requests delivering one issue produce ONE comment naming all of them', () => {
  // given — four pull requests, one issue. Per-PR rows all carried the same
  // `issue-100` marker, so publishing them would have overwritten one with the
  // next and left only whichever was applied last.
  const prs = [100, 101, 102, 103].map((number) => pr({ number, issueNumbers: [100], closesIssueNumbers: [] }));

  // when
  const result = render(prs, [], { version: '8.9.0', allowUnattributed: false });

  // then
  const entries = (result.commentsJson as { entries: { issueNumber: number; prNumbers: number[]; text: string }[] }).entries;
  const forIssue = entries.filter((e) => e.issueNumber === 100);
  assert.equal(forIssue.length, 1);
  assert.deepEqual(forIssue[0]!.prNumbers, [100, 101, 102, 103]);
  assert.match(forIssue[0]!.text, /by #100, #101, #102, #103/);
});

test('an issue is Released when ANY of its delivering pull requests closed it', () => {
  // given — only the last of three actually closed the issue
  const prs = [
    pr({ number: 200, issueNumbers: [100], closesIssueNumbers: [] }),
    pr({ number: 201, issueNumbers: [100], closesIssueNumbers: [] }),
    pr({ number: 202, issueNumbers: [100], closesIssueNumbers: [100] }),
  ];

  // when
  const result = render(prs, [], { version: '8.9.0', allowUnattributed: false });

  // then — one comment, and it reads as released rather than partial
  const entries = (result.commentsJson as { entries: { issueNumber: number; relationKind: string; text: string }[] }).entries;
  const entry = entries.find((e) => e.issueNumber === 100);
  assert.equal(entry?.relationKind, 'closing');
  assert.match(entry!.text, /Released in 8\.9\.0 \(#200, #201, #202\)/);
});

test('multi-release delivery idempotency: the earlier release never says Released even after the later one does', () => {
  const earlier = render([pr({ number: 20, issueNumbers: [100], closesIssueNumbers: [] })], [], {
    version: '8.8.5',
    allowUnattributed: false,
  });
  const later = render([pr({ number: 21, issueNumbers: [100], closesIssueNumbers: [100] })], [], {
    version: '8.8.6',
    allowUnattributed: false,
  });
  const earlierEntry = (earlier.commentsJson as { entries: { issueNumber: number; text: string }[] }).entries.find(
    (e) => e.issueNumber === 100,
  );
  const laterEntry = (later.commentsJson as { entries: { issueNumber: number; text: string }[] }).entries.find(
    (e) => e.issueNumber === 100,
  );
  assert.doesNotMatch(earlierEntry!.text, /Released/);
  assert.match(laterEntry!.text, /Released in 8\.8\.6/);
});

test('an internal-only section is present in the full asset but absent from the customer body', () => {
  const result = render([pr({ number: 30, section: 'Maintenance', visibility: 'internal', title: 'ci: bump runner' })], [], {
    version: '8.8.30',
    allowUnattributed: false,
  });
  assert.doesNotMatch(result.customerBody, /#30/);
  assert.match(result.fullAsset, /#30/);
});

test('a no-issue (opt-out) customer-visible PR renders under "Changes without a tracked issue", not its type section', () => {
  const result = render(
    [pr({ number: 40, title: 'fix: x', section: 'Bug Fixes', attributionSource: 'optOut', issueNumbers: [] })],
    [],
    { version: '8.8.30', allowUnattributed: false },
  );
  assert.match(result.customerBody, /## Changes without a tracked issue/);
  assert.match(result.customerBody, /#40/);
});

test('a bot-exempt PR (e.g. renovate) renders under its normal type section, NOT "Changes without a tracked issue" — the exemption is structural, not a declaration', () => {
  const result = render(
    [pr({ number: 41, title: 'deps: bump foo', section: 'Dependency updates', attributionSource: 'botExempt', issueNumbers: [] })],
    [],
    { version: '8.8.30', allowUnattributed: false },
  );
  assert.doesNotMatch(result.customerBody, /Changes without a tracked issue/);
  assert.match(result.customerBody, /## Dependency updates/);
  assert.match(result.customerBody, /#41/);
});

test('several PRs delivering one issue render as a single line naming every PR — four lines read as four features', () => {
  // given four PRs whose display title has already resolved to issue #55's title
  const delivering = [101, 102, 103, 104].map((number) =>
    pr({ number, title: 'Add agent history API', issueNumbers: [55], closesIssueNumbers: number === 104 ? [55] : [] }),
  );

  // when
  const body = render(delivering, [], { version: '8.8.38', allowUnattributed: false }).customerBody;

  // then the issue is named once, with every contributing PR as provenance
  const featureLines = body.split('\n').filter((line) => line.startsWith('- '));
  assert.equal(featureLines.length, 1);
  assert.equal(featureLines[0], '- Add agent history API (#55) — #101, #102, #103, #104');
});

test('an issue whose PRs span sections lands once, in the most customer-visible of them', () => {
  // given one issue delivered by a refactor, a feature, a test and a fix
  const delivering = [
    pr({ number: 101, title: 'Add agent history API', section: 'Maintenance', visibility: 'internal', issueNumbers: [55] }),
    pr({ number: 102, title: 'Add agent history API', section: 'Bug Fixes', issueNumbers: [55] }),
    pr({ number: 103, title: 'Add agent history API', section: 'Maintenance', visibility: 'internal', issueNumbers: [55] }),
    pr({ number: 104, title: 'Add agent history API', section: 'Features', issueNumbers: [55] }),
  ];

  // when
  const asset = render(delivering, [], { version: '8.8.38', allowUnattributed: false }).fullAsset;

  // then Features outranks Bug Fixes and Maintenance, and the entry appears there alone
  assert.match(asset, /## Features/);
  assert.doesNotMatch(asset, /## Bug Fixes/);
  assert.doesNotMatch(asset, /## Maintenance/);
  assert.equal(asset.split('\n').filter((line) => line.startsWith('- ')).length, 1);
});

test('the customer body names only the PRs it may show; the full asset names every contributor', () => {
  // The visibility filter decides which PRs an entry may cite, not just which
  // entries exist — a maintenance PR number in customer notes is noise.
  const delivering = [
    pr({ number: 101, title: 'Add agent history API', section: 'Maintenance', visibility: 'internal', issueNumbers: [55] }),
    pr({ number: 102, title: 'Add agent history API', section: 'Features', issueNumbers: [55] }),
  ];

  // when
  const result = render(delivering, [], { version: '8.8.38', allowUnattributed: false });

  // then
  assert.match(result.customerBody, /- Add agent history API \(#55\) — #102$/m);
  assert.match(result.fullAsset, /- Add agent history API \(#55\) — #101, #102$/m);
});

test('grouping never merges PRs that share no issue — two issues stay two lines', () => {
  const delivering = [
    pr({ number: 101, title: 'Add agent history API', issueNumbers: [55] }),
    pr({ number: 102, title: 'Add audit log export', issueNumbers: [56] }),
  ];

  const body = render(delivering, [], { version: '8.8.38', allowUnattributed: false }).customerBody;

  assert.equal(body.split('\n').filter((line) => line.startsWith('- ')).length, 2);
  assert.match(body, /Add agent history API \(#55\) — #101/);
  assert.match(body, /Add audit log export \(#56\) — #102/);
});

test('BREAKING CHANGE is cross-listed at the top of the customer body in addition to its normal section', () => {
  const result = render([pr({ number: 50, breaking: true })], [], { version: '8.8.30', allowUnattributed: false });
  assert.match(result.customerBody, /## Breaking changes/);
  assert.match(result.customerBody, /#50/);
  assert.match(result.customerBody, /## Features/);
});
