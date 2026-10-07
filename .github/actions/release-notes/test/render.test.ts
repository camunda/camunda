import assert from 'node:assert/strict';
import { test } from 'node:test';
import { render, SCHEMA_VERSION, emptyCustomerBodyWarning, RELEASE_BODY_LIMIT } from '../src/render';
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
    deliveryPath: 'direct',
    ...overrides,
  };
}

test('a single closing PR appears in the customer body under its section, linking the issue', () => {
  const result = render([pr({ number: 1, issueNumbers: [100], closesIssueNumbers: [100] })], {
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
  const result = render(unattributed, { version: '8.8.30', allowUnattributed: false });

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
  const result = render([pr({ number: 9 })], { version: '8.9.0', allowUnattributed: false, warnings });

  // then — a log is not an artifact: nothing downstream can read, diff or
  // archive one, so the audit guarantees have to survive in the file too.
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, warnings);
});

test('audit.json carries an empty warning list rather than omitting the field', () => {
  // when — a clean run
  const result = render([pr({ number: 9 })], { version: '8.9.0', allowUnattributed: false });

  // then — a consumer never has to distinguish absent from empty
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, []);
});

test('a failed run records no overrides — nothing was overridden', () => {
  // given — the guard failed, so no exception was approved
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];

  // when
  const result = render(unattributed, { version: '8.8.30', allowUnattributed: false });

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
  const reason = render(bucket, { version: '8.8.30', allowUnattributed: false }).failureReason ?? '';

  assert.match(reason, /No issue reference found: #2/);
  assert.match(reason, /Every referenced issue was unresolvable: #3/);
  // #3 must not be described as having no reference — that is the misread.
  assert.doesNotMatch(reason, /No issue reference found: [^.]*#3/);
  assert.match(reason, /allow-unattributed=true/);
});

test('the gate message names only the failure kinds actually present', () => {
  const onlyDead = [pr({ number: 7, attributionSource: 'resolutionFailed', issueNumbers: [] })];
  const reason = render(onlyDead, { version: '8.8.30', allowUnattributed: false }).failureReason ?? '';

  assert.match(reason, /Every referenced issue was unresolvable: #7/);
  assert.doesNotMatch(reason, /No issue reference found/);
});

test('allow-unattributed with a reason overrides the failure and records the reason in audit.json', () => {
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];
  const result = render(unattributed, {
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
  const result = render(unattributed, { version: '8.8.30', allowUnattributed: true });
  assert.ok(result.failureReason);
});

test('should exclude merge-type PRs from the unattributed guard', () => {
  // given
  const prs = [pr({ section: null, attributionSource: 'unattributed', issueNumbers: [] })];

  // when
  const result = render(prs, { version: '8.9.19', allowUnattributed: false });

  // then
  assert.equal(result.failureReason, undefined);
  assert.equal(result.customerBody, '');
  assert.equal(result.fullAsset, '');
  assert.deepEqual((result.auditJson as { overrides: unknown[] }).overrides, []);
});

test('all four JSON outputs carry the literal schemaVersion', () => {
  const result = render([pr()], { version: '8.8.30', allowUnattributed: false });
  for (const doc of [result.changelogJson, result.labelsJson, result.auditJson, result.commentsJson]) {
    assert.equal((doc as { schemaVersion: string }).schemaVersion, SCHEMA_VERSION);
  }
});

test('changelog.json records deliveryPath alongside attributionSource for every PR', () => {
  // given — a downstream consumer must be able to tell a backport hop from
  // direct delivery without re-deriving it from the PR body
  const result = render([pr({ number: 1, deliveryPath: 'backportHop' }), pr({ number: 2, deliveryPath: 'direct' })], {
    version: '8.8.30',
    allowUnattributed: false,
  });

  // when
  const prs = (result.changelogJson as { prs: { number: number; deliveryPath: string }[] }).prs;

  // then
  assert.equal(prs.find((p) => p.number === 1)?.deliveryPath, 'backportHop');
  assert.equal(prs.find((p) => p.number === 2)?.deliveryPath, 'direct');
});

test('an issue this PR actually closes gets a "Released" comment', () => {
  const result = render([pr({ number: 10, issueNumbers: [100], closesIssueNumbers: [100] })], {
    version: '8.8.6',
    allowUnattributed: false,
  });
  const entries = (result.commentsJson as { entries: { issueNumber: number; relationKind: string; text: string }[] }).entries;
  const entry = entries.find((e) => e.issueNumber === 100);
  assert.equal(entry?.relationKind, 'closing');
  assert.match(entry!.text, /Released in 8\.8\.6/);
});

test('an issue this PR only contributes to (does not close) gets a "Partially delivered" comment, never "Released"', () => {
  const result = render([pr({ number: 11, issueNumbers: [100], closesIssueNumbers: [] })], {
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
  const result = render(prs, { version: '8.9.0', allowUnattributed: false });

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
  const result = render(prs, { version: '8.9.0', allowUnattributed: false });

  // then — one comment, and it reads as released rather than partial
  const entries = (result.commentsJson as { entries: { issueNumber: number; relationKind: string; text: string }[] }).entries;
  const entry = entries.find((e) => e.issueNumber === 100);
  assert.equal(entry?.relationKind, 'closing');
  assert.match(entry!.text, /Released in 8\.9\.0 \(#200, #201, #202\)/);
});

test('multi-release delivery idempotency: the earlier release never says Released even after the later one does', () => {
  const earlier = render([pr({ number: 20, issueNumbers: [100], closesIssueNumbers: [] })], {
    version: '8.8.5',
    allowUnattributed: false,
  });
  const later = render([pr({ number: 21, issueNumbers: [100], closesIssueNumbers: [100] })], {
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

test('when everything fits, the release description is the full asset — nothing is held back for being internal', () => {
  // given — an internal Maintenance PR, a kind/task-tracked fix and an unattributed fix
  const result = render(
    [
      pr({ number: 30, section: 'Maintenance', visibility: 'internal', title: 'ci: bump runner', issueNumbers: [] }),
      pr({ number: 31, section: 'Bug Fixes', visibility: 'internal', title: 'Tidy retries', issueNumbers: [310] }),
      pr({ number: 32, section: 'Bug Fixes', title: 'fix: x', issueNumbers: [], attributionSource: 'unattributed' }),
    ],
    { version: '8.8.30', allowUnattributed: true, unattributedReason: 'test' },
  );

  // then
  assert.equal(result.customerBody, result.fullAsset);
  assert.match(result.customerBody, /#30[\s\S]*#31[\s\S]*#32|#31[\s\S]*#32[\s\S]*#30/);
  assert.doesNotMatch(result.customerBody, /truncated/);
});

test('emptyCustomerBodyWarning: no warning when there was no attributed work at all', () => {
  assert.equal(emptyCustomerBodyWarning(false, ''), undefined);
});

test('emptyCustomerBodyWarning: no warning when the body is non-empty', () => {
  assert.equal(emptyCustomerBodyWarning(true, '## Features\n\n- x (#1)'), undefined);
});

test('emptyCustomerBodyWarning: warns when work was attributed but the body is empty', () => {
  assert.ok(emptyCustomerBodyWarning(true, '')?.includes('Customer-facing body is empty'));
});

test('a maintenance-only release still gets a non-empty description, since nothing is held back', () => {
  const result = render([pr({ number: 30, section: 'Maintenance', visibility: 'internal', title: 'ci: bump runner' })], {
    version: '8.8.30',
    allowUnattributed: false,
  });
  assert.match(result.customerBody, /#30/);
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, []);
});

test('an empty customer body from a genuinely empty release (nothing attributed) does not warn', () => {
  // given — no attributed PRs at all, so there is nothing to be suspicious of
  const result = render([], { version: '8.8.30', allowUnattributed: false });
  assert.equal(result.customerBody, '');
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, []);
});

test('a non-empty customer body never triggers the empty-body warning', () => {
  const result = render([pr()], { version: '8.8.30', allowUnattributed: false });
  assert.notEqual(result.customerBody, '');
  assert.deepEqual((result.auditJson as { warnings: string[] }).warnings, []);
});

test('a no-issue (opt-out) customer-visible PR renders under its title-type section, not a section of its own', () => {
  const result = render(
    [pr({ number: 40, title: 'fix: x', section: 'Bug Fixes', attributionSource: 'optOut', issueNumbers: [] })],
    { version: '8.8.30', allowUnattributed: false },
  );
  assert.match(result.customerBody, /## Bug Fixes\n\n- fix: x \(#40\)/);
  assert.doesNotMatch(result.customerBody, /tracked issue|Other changes/);
});

test('with a repository, every issue and PR number in both bodies is a link; without one they stay plain', () => {
  // given a PR delivering an issue, plus an issue-less dependency bump in the full asset
  const prs = [
    pr({ number: 7, title: 'Add thing', issueNumbers: [55] }),
    pr({
      number: 8,
      title: 'deps: bump foo',
      section: 'Dependency updates',
      attributionSource: 'botExempt',
      issueNumbers: [],
      dependencies: [{ name: 'foo', from: '1.0', to: '1.1' }],
    }),
  ];

  // when
  const linked = render(prs, { version: '8.8.30', allowUnattributed: false, repository: 'camunda/camunda' });
  const plain = render(prs, { version: '8.8.30', allowUnattributed: false });

  // then
  const issue = '[#55](https://github.com/camunda/camunda/pull/55)';
  const pull = '[#7](https://github.com/camunda/camunda/pull/7)';
  assert.match(linked.customerBody, new RegExp(`\\(${issue.replace(/[[\]().]/g, '\\$&')}\\) — ${pull.replace(/[[\]().]/g, '\\$&')}`));
  assert.match(linked.fullAsset, /- foo: 1\.0 → 1\.1 \(\[#8\]\(https:\/\/github\.com\/camunda\/camunda\/pull\/8\)\)/);
  assert.match(plain.customerBody, /\(#55\) — #7/);
});

test('a bot-exempt PR (e.g. renovate) renders under its normal type section — the exemption is structural, not a declaration', () => {
  const result = render(
    [pr({ number: 41, title: 'deps: bump foo', section: 'Dependency updates', attributionSource: 'botExempt', issueNumbers: [] })],
    { version: '8.8.30', allowUnattributed: false },
  );
  assert.match(result.customerBody, /## Dependency updates/);
  assert.match(result.customerBody, /#41/);
});

test('several PRs delivering one issue render as a single line naming every PR — four lines read as four features', () => {
  // given four PRs whose display title has already resolved to issue #55's title
  const delivering = [101, 102, 103, 104].map((number) =>
    pr({ number, title: 'Add agent history API', issueNumbers: [55], closesIssueNumbers: number === 104 ? [55] : [] }),
  );

  // when
  const body = render(delivering, { version: '8.8.38', allowUnattributed: false }).customerBody;

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
  const asset = render(delivering, { version: '8.8.38', allowUnattributed: false }).fullAsset;

  // then Features outranks Bug Fixes and Maintenance, and the entry appears there alone
  assert.match(asset, /## Features/);
  assert.doesNotMatch(asset, /## Bug Fixes/);
  assert.doesNotMatch(asset, /## Maintenance/);
  assert.equal(asset.split('\n').filter((line) => line.startsWith('- ')).length, 1);
});

test('an entry names every contributing PR in both bodies', () => {
  const delivering = [
    pr({ number: 101, title: 'Add agent history API', section: 'Maintenance', visibility: 'internal', issueNumbers: [55] }),
    pr({ number: 102, title: 'Add agent history API', section: 'Features', issueNumbers: [55], closesIssueNumbers: [55] }),
  ];

  // when
  const result = render(delivering, { version: '8.8.38', allowUnattributed: false });

  // then
  assert.match(result.customerBody, /- Add agent history API \(#55\) — #101, #102$/m);
  assert.match(result.fullAsset, /- Add agent history API \(#55\) — #101, #102$/m);
});

test('grouping never merges PRs that share no issue — two issues stay two lines', () => {
  const delivering = [
    pr({ number: 101, title: 'Add agent history API', issueNumbers: [55] }),
    pr({ number: 102, title: 'Add audit log export', issueNumbers: [56] }),
  ];

  const body = render(delivering, { version: '8.8.38', allowUnattributed: false }).customerBody;

  assert.equal(body.split('\n').filter((line) => line.startsWith('- ')).length, 2);
  assert.match(body, /Add agent history API \(#55\) — #101/);
  assert.match(body, /Add audit log export \(#56\) — #102/);
});

test('BREAKING CHANGE is cross-listed at the top of the customer body in addition to its normal section', () => {
  const result = render([pr({ number: 50, breaking: true })], { version: '8.8.30', allowUnattributed: false });
  assert.match(result.customerBody, /## Breaking changes/);
  assert.match(result.customerBody, /#50/);
  assert.match(result.customerBody, /## Features/);
});

for (const breakingPr of [1, 2]) {
  test(`should preserve breaking changes from dependency PR #${breakingPr} when grouping packages`, () => {
    // given
    const prs = [2, 1].map((number) =>
      pr({
        number,
        section: 'Dependency updates',
        attributionSource: 'botExempt',
        issueNumbers: [],
        breaking: number === breakingPr,
        dependencies: [
          { name: 'pkg', from: `${number}.0`, to: `${number + 1}.0` },
          { name: 'other', from: `${number}.0`, to: `${number + 1}.0` },
        ],
      }),
    );

    // when
    const result = render(prs, { version: '8.9.19', allowUnattributed: false });

    // then
    for (const body of [result.customerBody, result.fullAsset]) {
      assert.match(body, /^## Breaking changes\n\n- pkg: 1\.0 → 3\.0 \(#2, #1\)\n- other: 1\.0 → 3\.0 \(#2, #1\)/);
      assert.match(body, /## Dependency updates/);
    }
  });
}

test('an issue still open after this release is marked partially delivered', () => {
  // given — grouping puts one line under the issue's own title, which reads as
  // the whole feature shipping even when the issue is still open
  const prs = [
    pr({ number: 101, title: 'Add agent history API', section: 'Features', issueNumbers: [55], openIssueNumbers: [55] }),
    pr({ number: 102, title: 'Add agent history API', section: 'Features', issueNumbers: [55], openIssueNumbers: [55] }),
  ];

  // when
  const result = render(prs, { version: '8.9.0', allowUnattributed: false });

  // then
  assert.match(result.customerBody, /- Add agent history API \(#55\) — #101, #102 \(partially delivered\)$/m);
});

test('a closed issue carries no partial marker, even when no pull request is recorded as its closer', () => {
  // given — 8.9.19's #55699 is CLOSED/COMPLETED with no recorded closer: a
  // human clicked Close. Keying the marker on "who closed it" called that
  // partially delivered, which is a false claim about finished work.
  const prs = [
    pr({ number: 101, title: 'Add agent history API', section: 'Features', issueNumbers: [55], closesIssueNumbers: [], openIssueNumbers: [] }),
  ];

  // when
  const result = render(prs, { version: '8.9.0', allowUnattributed: false });

  // then
  assert.match(result.customerBody, /- Add agent history API \(#55\) — #101$/m);
});

test('an entry with no linked issue is never marked partial', () => {
  // given — an opt-out pull request has no issue to be partial about
  const result = render([pr({ number: 61909, title: 'fix: use exact long position', attributionSource: 'optOut', issueNumbers: [] })], {
    version: '8.9.19',
    allowUnattributed: false,
  });

  // then
  assert.doesNotMatch(result.customerBody, /partially delivered/);
});

test('repeated updates of one dependency collapse to one first-to-last line', () => {
  // given — the walk is newest-first, so #61527 (newest) moved it to 4.8.196
  // and #61530 (oldest) started it at 4.8.193
  const bump = (number: number, from: string, to: string) =>
    pr({
      number,
      section: 'Dependency updates',
      issueNumbers: [],
      title: `io.github.classgraph:classgraph: ${from} → ${to}`,
      dependencies: [{ name: 'io.github.classgraph:classgraph', from, to }],
    });
  const prs = [bump(61527, '4.8.195', '4.8.196'), bump(61528, '4.8.194', '4.8.195'), bump(61530, '4.8.193', '4.8.194')];

  // when
  const result = render(prs, { version: '8.9.19', allowUnattributed: false });

  // then — one line, the range the release actually moved it through, every
  // pull request still cited
  assert.match(result.fullAsset, /- io\.github\.classgraph:classgraph: 4\.8\.193 → 4\.8\.196 \(#61527, #61528, #61530\)$/m);
  assert.equal(result.fullAsset.split('\n').filter((line) => line.includes('classgraph')).length, 1);
  assert.doesNotMatch(result.customerBody, /## Breaking changes/);
  assert.doesNotMatch(result.fullAsset, /## Breaking changes/);
});

test('one renovate pull request listing a package twice collapses too', () => {
  // given — a grouped renovate PR whose body table carries two rows for one
  // package; 8.9.19 shipped exactly this as a doubled browserslist line
  const prs = [
    pr({
      number: 61684,
      section: 'Dependency updates',
      issueNumbers: [],
      title: 'browserslist: 4.28.1 → 4.28.7; browserslist: 4.28.2 → 4.28.7',
      dependencies: [
        { name: 'browserslist', from: '4.28.2', to: '4.28.7' },
        { name: 'browserslist', from: '4.28.1', to: '4.28.7' },
      ],
    }),
  ];

  // when
  const result = render(prs, { version: '8.9.19', allowUnattributed: false });

  // then
  assert.match(result.fullAsset, /- browserslist: 4\.28\.1 → 4\.28\.7 \(#61684\)$/m);
});

test('one pull request bumping several packages produces one line each', () => {
  // given
  const prs = [
    pr({
      number: 900,
      section: 'Dependency updates',
      issueNumbers: [],
      title: 'grouped',
      dependencies: [
        { name: 'left', from: '1.0', to: '1.1' },
        { name: 'right', from: '2.0', to: '2.1' },
      ],
    }),
  ];

  // when
  const result = render(prs, { version: '8.9.19', allowUnattributed: false });

  // then — a reader scanning for one package finds it on its own line
  assert.match(result.fullAsset, /- left: 1\.0 → 1\.1 \(#900\)$/m);
  assert.match(result.fullAsset, /- right: 2\.0 → 2\.1 \(#900\)$/m);
});

test('a dependency that is downgraded then bumped back up shows the release\'s actual start and end, not the numeric extremes', () => {
  // given — walk is newest-first: #2 (newest) moved it 1 → 1.5, #1 (oldest,
  // chronologically first) moved it 2 → 1. The release actually moved it
  // 2 → 1.5; numeric min/max across both PRs would wrongly say 1 → 1.5
  const bump = (number: number, from: string, to: string) =>
    pr({ number, section: 'Dependency updates', issueNumbers: [], title: 'x', dependencies: [{ name: 'pkg', from, to }] });

  // when
  const result = render([bump(2, '1', '1.5'), bump(1, '2', '1')], { version: '8.9.19', allowUnattributed: false });

  // then
  assert.match(result.fullAsset, /- pkg: 2 → 1\.5 \(#2, #1\)$/m);
});

test('an unorderable version pair falls back to walk order rather than inventing a range', () => {
  // given — action digests and short shas have no numeric order, and the walk
  // is newest-first, so the oldest pull request holds the release's start
  const bump = (number: number, from: string, to: string) =>
    pr({
      number,
      section: 'Dependency updates',
      issueNumbers: [],
      title: 'x',
      dependencies: [{ name: 'camunda/infra-global-github-actions', from, to }],
    });

  // when
  const result = render([bump(2, 'bbbbbbb', 'ccccccc'), bump(1, 'aaaaaaa', 'bbbbbbb')], {
    version: '8.9.19',
    allowUnattributed: false,
  });

  // then
  assert.match(result.fullAsset, /- camunda\/infra-global-github-actions: aaaaaaa → ccccccc \(#2, #1\)$/m);
});

function bump(number: number, overrides: Partial<RenderPrInput> = {}): RenderPrInput {
  return pr({
    number,
    title: `deps: bump pkg${number}`,
    section: 'Dependency updates',
    attributionSource: 'botExempt',
    issueNumbers: [],
    dependencies: [{ name: `pkg${number}`, from: '1.0', to: '2.0' }],
    ...overrides,
  });
}

/** Pads a release past GitHub's limit: one near-limit entry plus `count` issue-less bumps, so
 *  that dropping the bumps (and adding the banner and pointer) is what brings it back under. */
function overLimit(prs: RenderPrInput[], count = 40): RenderPrInput[] {
  const filler = pr({ number: 900, title: 'x'.repeat(RELEASE_BODY_LIMIT - 800), issueNumbers: [900] });
  const bumps = Array.from({ length: count }, (_, i) => bump(1000 + i, { dependencies: [{ name: `filler${i}`, from: '1.0', to: '2.0' }] }));
  return [filler, ...prs, ...bumps];
}

test('over the limit, issue-less dependency bumps are dropped first and replaced by a pointer', () => {
  // given — a release too big for GitHub, with a Maintenance PR that should survive the first drop
  const maintenance = pr({ number: 2, section: 'Maintenance', visibility: 'internal', title: 'ci: bump runner', issueNumbers: [] });
  const result = render(overLimit([maintenance]), { version: '8.10.0', allowUnattributed: false, repository: 'camunda/camunda' });

  // then
  assert.ok(result.customerBody.length <= RELEASE_BODY_LIMIT);
  assert.doesNotMatch(result.customerBody, /filler0/);
  assert.match(result.customerBody, /\n\n40 dependency updates are listed in the full changelog, `CHANGELOG-8\.10\.0\.md`\./);
  assert.match(result.customerBody, /## Maintenance/);
  assert.match(result.fullAsset, /- filler0: 1\.0 → 2\.0/);
});

test('the truncation banner names the real asset URL and is counted against the limit', () => {
  const result = render(overLimit([]), { version: '8.10.0', allowUnattributed: false, repository: 'camunda/camunda' });

  assert.ok(
    result.customerBody.startsWith(
      '> [!WARNING]\n> The release notes are truncated, for full list of changes please download the full assets from [here](https://github.com/camunda/camunda/releases/download/8.10.0/CHANGELOG-8.10.0.md).',
    ),
  );
  assert.ok(result.customerBody.length <= RELEASE_BODY_LIMIT);
  assert.ok((result.auditJson as { warnings: string[] }).warnings.some((line) => line.includes('truncated')));
});

test('drops go in priority order: Maintenance only after dependency bumps, Reverts after Maintenance', () => {
  // given — filler so large that dropping the bumps alone is not enough
  const filler = pr({ number: 900, title: 'x'.repeat(RELEASE_BODY_LIMIT - 700), issueNumbers: [900] });
  const maintenance = pr({ number: 2, section: 'Maintenance', visibility: 'internal', title: `ci: ${'m'.repeat(900)}`, issueNumbers: [] });
  const revert = pr({ number: 3, section: 'Reverts', title: 'revert: y', issueNumbers: [] });
  const result = render([filler, maintenance, revert, bump(4)], { version: '8.10.0', allowUnattributed: false });

  // then — bump and Maintenance are gone, the smaller Revert section still fits
  assert.doesNotMatch(result.customerBody, /pkg4|## Maintenance/);
  assert.match(result.customerBody, /## Reverts/);
  assert.match(result.customerBody, /truncated/);
});

test('a breaking change is never dropped, whatever its section', () => {
  const filler = pr({ number: 900, title: 'x'.repeat(RELEASE_BODY_LIMIT - 700), issueNumbers: [900] });
  const breakingMaintenance = pr({ number: 2, section: 'Maintenance', visibility: 'internal', title: 'ci: breaking', issueNumbers: [], breaking: true });
  const result = render([filler, breakingMaintenance, ...overLimit([]).slice(1)], { version: '8.10.0', allowUnattributed: false });

  assert.match(result.customerBody, /## Breaking changes\n\n- ci: breaking \(#2\)/);
});

test('if even the undroppable sections do not fit, trailing entries are cut and the banner stays', () => {
  const result = render([pr({ number: 1, title: 'x'.repeat(RELEASE_BODY_LIMIT) })], { version: '8.10.0', allowUnattributed: false });

  assert.ok(result.customerBody.startsWith('> [!WARNING]'));
  assert.ok(result.customerBody.length <= RELEASE_BODY_LIMIT);
  assert.ok((result.auditJson as { warnings: string[] }).warnings.some((line) => line.includes('the last 1 entries')));
});

test('a bump without a parseable package table is dropped with the other bumps, and counted in the pointer', () => {
  // given — a c8run-style `deps:` PR whose body has no table, beside 40 parsed bumps
  const unparsed = pr({ number: 5, title: 'deps: update c8run versions to 8.9.22', section: 'Dependency updates', attributionSource: 'botExempt', issueNumbers: [] });
  const result = render(overLimit([unparsed]), { version: '8.10.0', allowUnattributed: false });

  // then — all or none: no bump line survives, and the pointer counts the c8run line too
  assert.doesNotMatch(result.customerBody, /c8run|filler0/);
  assert.match(result.customerBody, /## Dependency updates\n\n41 dependency updates are listed in the full changelog/);
});

test('a dependency PR that delivers an issue stays in the customer body — a CVE fix is customer news', () => {
  // given
  const result = render([bump(4, { issueNumbers: [400], title: 'deps: bump pkg4 to fix CVE' })], {
    version: '8.10.0',
    allowUnattributed: false,
  });

  // then
  assert.match(result.customerBody, /## Dependency updates\n\n- deps: bump pkg4 to fix CVE \(#400\) — #4/);
  assert.doesNotMatch(result.customerBody, /listed in the full changelog/);
});

test('every bump of a package that one breaking bump touched stays in the description, so its range does not split', () => {
  // given — #5 is breaking, #6 bumps the same package later, #7 an unrelated one
  const result = render(
    overLimit([
      bump(6, { dependencies: [{ name: 'shared', from: '2.0', to: '3.0' }] }),
      bump(5, { breaking: true, dependencies: [{ name: 'shared', from: '1.0', to: '2.0' }] }),
      bump(7),
    ]),
    { version: '8.10.0', allowUnattributed: false },
  );

  // then
  assert.match(result.customerBody, /## Breaking changes\n\n- shared: 1\.0 → 3\.0 \(#6, #5\)/);
  assert.doesNotMatch(result.customerBody, /pkg7/);
  assert.match(result.customerBody, /## Dependency updates\n\n- shared: 1\.0 → 3\.0 \(#6, #5\)\n\n41 other dependency updates are listed/);
});

test('a bump sharing a package with a kept bump is kept too, transitively, so no range splits', () => {
  // given — #5 is breaking on `shared`; #6 bumps `shared` and `other`; #4 bumps only `other`
  const result = render(
    overLimit([
      bump(6, { dependencies: [{ name: 'shared', from: '2.0', to: '3.0' }, { name: 'other', from: '2.0', to: '3.0' }] }),
      bump(5, { breaking: true, dependencies: [{ name: 'shared', from: '1.0', to: '2.0' }] }),
      bump(4, { dependencies: [{ name: 'other', from: '1.0', to: '2.0' }] }),
    ]),
    { version: '8.10.0', allowUnattributed: false },
  );

  // then
  assert.match(result.customerBody, /- other: 1\.0 → 3\.0 \(#6, #4\)/);
});

test('the full-changelog pointer counts packages, not pull requests', () => {
  // given — two pull requests bumping the same package are one line in the full changelog
  const result = render(
    overLimit([bump(9, { dependencies: [{ name: 'pkg', from: '2.0', to: '3.0' }] }), bump(8, { dependencies: [{ name: 'pkg', from: '1.0', to: '2.0' }] })]),
    { version: '8.10.0', allowUnattributed: false },
  );

  // then — 40 filler packages plus `pkg`
  assert.match(result.customerBody, /41 dependency updates are listed in the full changelog/);
});

test('a release of only bot bumps lists them when they fit', () => {
  const result = render([bump(8)], { version: '8.9.23', allowUnattributed: false });
  assert.equal(result.customerBody, '## Dependency updates\n\n- pkg8: 1.0 → 2.0 (#8)');
});
