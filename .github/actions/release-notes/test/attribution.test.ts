import assert from 'node:assert/strict';
import { test } from 'node:test';
import { decideAttribution, evaluatePostGateAnomaly, hopToBackportOriginal } from '../src/attribution';
import type { AttributionInput } from '../src/attribution/types';
import type { ParsedRef, RefKind, RefTarget, ResolvedRef } from '../src/types';

function ref(
  number: number,
  kind: RefKind,
  target: RefTarget,
  opts: { crossRepo?: boolean; repo?: string | null } = {},
): ResolvedRef {
  const base: ParsedRef = { raw: `#${number}`, number, repo: opts.repo ?? null, keyword: null, kind, index: 0 };
  return { ...base, target, crossRepo: opts.crossRepo ?? false };
}

function input(overrides: Partial<AttributionInput> = {}): AttributionInput {
  return {
    optOut: false,
    sectionRefs: [],
    closingIssuesReferences: [],
    legacyRefs: [],
    ...overrides,
  };
}

test('closing ref in section attributes to that issue', () => {
  const d = decideAttribution(input({ sectionRefs: [ref(100, 'closing', 'issue')] }));
  assert.equal(d.source, 'section');
  assert.deepEqual(d.issueNumbers, [100]);
  assert.equal(d.deliveryPath, 'direct');
});

test('contributor ref in section attributes the same way as a closing ref', () => {
  const d = decideAttribution(input({ sectionRefs: [ref(100, 'contributor', 'issue')] }));
  assert.equal(d.source, 'section');
  assert.deepEqual(d.issueNumbers, [100]);
});

test('opt-out ticked with no ref anywhere', () => {
  const d = decideAttribution(input({ optOut: true }));
  assert.equal(d.source, 'optOut');
  assert.deepEqual(d.issueNumbers, []);
});

test('native closingIssuesReferences used when the section is empty', () => {
  const d = decideAttribution(input({ closingIssuesReferences: [100] }));
  assert.equal(d.source, 'closingIssuesReferences');
  assert.deepEqual(d.issueNumbers, [100]);
});

test('legacy body-wide scan used when section and native field are both empty', () => {
  const d = decideAttribution(input({ legacyRefs: [ref(100, 'closing', 'issue')] }));
  assert.equal(d.source, 'legacyBodyScan');
  assert.deepEqual(d.issueNumbers, [100]);
});

test('partial resolution: dead ref audited, live ref still attributed', () => {
  const d = decideAttribution(
    input({ sectionRefs: [ref(100, 'closing', 'issue'), ref(999, 'closing', 'missing')] }),
  );
  assert.equal(d.source, 'section');
  assert.deepEqual(d.issueNumbers, [100]);
  assert.ok(d.reasons.some((r) => r.includes('#999')));
});

test('all refs in the section dead: resolutionFailed, no fallthrough to native/legacy', () => {
  const d = decideAttribution(
    input({ sectionRefs: [ref(999, 'closing', 'missing')], closingIssuesReferences: [100] }),
  );
  assert.equal(d.source, 'resolutionFailed');
  assert.deepEqual(d.issueNumbers, []);
});

test('nothing found anywhere: unattributed', () => {
  const d = decideAttribution(input());
  assert.equal(d.source, 'unattributed');
  assert.deepEqual(d.issueNumbers, []);
});

test('cross-repo section refs are not eligible, so an empty section falls through to native', () => {
  const d = decideAttribution(
    input({
      sectionRefs: [ref(7, 'closing', 'missing', { crossRepo: true, repo: 'camunda/other' })],
      closingIssuesReferences: [100],
    }),
  );
  assert.equal(d.source, 'closingIssuesReferences');
  assert.deepEqual(d.issueNumbers, [100]);
});

test('a backport marker alone in the section does not count as a section ref', () => {
  const d = decideAttribution(
    input({ sectionRefs: [ref(200, 'backport', 'pullRequest')], closingIssuesReferences: [100] }),
  );
  assert.equal(d.source, 'closingIssuesReferences');
});

test('multiple live refs in the section all attribute', () => {
  const d = decideAttribution(
    input({ sectionRefs: [ref(100, 'closing', 'issue'), ref(101, 'contributor', 'issue')] }),
  );
  assert.equal(d.source, 'section');
  assert.deepEqual(d.issueNumbers, [100, 101]);
});

test('backport hop copies the original decision but flips deliveryPath', () => {
  const original = decideAttribution(input({ sectionRefs: [ref(200, 'closing', 'issue')] }));
  const hopped = hopToBackportOriginal(original);
  assert.equal(hopped.source, 'section');
  assert.deepEqual(hopped.issueNumbers, [200]);
  assert.equal(hopped.deliveryPath, 'backportHop');
});

test('post-gate fallback attribution anomaly fires for a fallback source merged after the watermark', () => {
  const anomaly = evaluatePostGateAnomaly({
    mergedAt: '2026-08-01T00:00:00Z',
    gateRequiredAt: '2026-07-01T00:00:00Z',
    source: 'legacyBodyScan',
  });
  assert.equal(anomaly, 'post_gate_fallback_attribution');
});

test('no anomaly when the merge predates the watermark', () => {
  const anomaly = evaluatePostGateAnomaly({
    mergedAt: '2026-06-01T00:00:00Z',
    gateRequiredAt: '2026-07-01T00:00:00Z',
    source: 'legacyBodyScan',
  });
  assert.equal(anomaly, undefined);
});

test('no anomaly for a section-attributed PR merged after the watermark (the expected path)', () => {
  const anomaly = evaluatePostGateAnomaly({
    mergedAt: '2026-08-01T00:00:00Z',
    gateRequiredAt: '2026-07-01T00:00:00Z',
    source: 'section',
  });
  assert.equal(anomaly, undefined);
});

test('no anomaly when the branch has no recorded watermark yet', () => {
  const anomaly = evaluatePostGateAnomaly({
    mergedAt: '2026-08-01T00:00:00Z',
    gateRequiredAt: null,
    source: 'legacyBodyScan',
  });
  assert.equal(anomaly, undefined);
});

test('legacy scan ignores PR refs, backport markers and cross-repo refs', () => {
  const d = decideAttribution(
    input({
      legacyRefs: [
        ref(1, 'closing', 'pullRequest'),
        ref(2, 'backport', 'issue'),
        ref(3, 'closing', 'issue', { crossRepo: true, repo: 'camunda/other' }),
        ref(100, 'closing', 'issue'),
      ],
    }),
  );
  assert.equal(d.source, 'legacyBodyScan');
  assert.deepEqual(d.issueNumbers, [100]);
});
