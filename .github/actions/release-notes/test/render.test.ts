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

test('an unattributed bucket reports a failure reason by default, but still renders every output — audit.json must exist to explain why', () => {
  const unattributed = [pr({ number: 2, attributionSource: 'unattributed', issueNumbers: [] })];
  const result = render([], unattributed, { version: '8.8.30', allowUnattributed: false });
  assert.match(result.failureReason ?? '', /#2/);
  assert.ok(
    (result.auditJson as { overrides: { number: number; reason: string }[] }).overrides.some((o) => o.number === 2),
  );
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

test('BREAKING CHANGE is cross-listed at the top of the customer body in addition to its normal section', () => {
  const result = render([pr({ number: 50, breaking: true })], [], { version: '8.8.30', allowUnattributed: false });
  assert.match(result.customerBody, /## Breaking changes/);
  assert.match(result.customerBody, /#50/);
  assert.match(result.customerBody, /## Features/);
});
