import assert from 'node:assert/strict';
import { test } from 'node:test';
import { processPr } from '../src/pipeline';
import type { PipelinePrInput, PipelineResolver } from '../src/pipeline';
import type { ParsedRef, ResolvedRef } from '../src/types';

/** A fake resolver: classifies every ref number 100 as a live issue, 999 as missing. */
function fakeResolver(originals: Record<number, { body: string; title: string; authorLogin?: string }> = {}): PipelineResolver {
  return {
    async resolveRefs(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
      return refs.map((ref) => ({ ...ref, target: ref.number === 999 ? 'missing' : 'issue', crossRepo: false }));
    },
    async fetchOriginalPull(number: number) {
      return originals[number] ?? null;
    },
  };
}

function prInput(overrides: Partial<PipelinePrInput> = {}): PipelinePrInput {
  return {
    number: 1,
    title: 'feat: add batch delete API',
    body: '## Related issues\ncloses #100',
    mergedAt: '2026-08-01T00:00:00Z',
    labels: [],
    closingIssuesReferences: [],
    ...overrides,
  };
}

test('a plain direct PR attributes via its section and categorizes by its own title', async () => {
  const out = await processPr(fakeResolver(), prInput(), { gateRequiredAt: null });
  assert.equal(out.attribution.source, 'section');
  assert.deepEqual(out.attribution.issueNumbers, [100]);
  assert.equal(out.categorization.section, 'Features');
});

test('a human-opened backport keeps [Backport ...] out of its displayed title', async () => {
  const out = await processPr(
    fakeResolver(),
    prInput({ title: '[Backport stable/8.8] fix: correct retry backoff', body: '## Related issues\ncloses #100' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.title, 'fix: correct retry backoff');
});

test('an inherit-original bot backport displays the ORIGINAL title, not its own placeholder', async () => {
  const resolver = fakeResolver({
    200: { body: '## Related issues\ncloses #100', title: '[Backport stable/8.8] fix: correct retry backoff' },
  });
  const out = await processPr(
    resolver,
    prInput({
      number: 300,
      title: 'chore stuff', // the bot's own title doesn't parse — inherit-original must rescue display too
      body: 'Backport of #200',
      authorLogin: 'monorepo-devops-automation[bot]',
    }),
    { gateRequiredAt: null },
  );
  assert.equal(out.title, 'fix: correct retry backoff');
});

test('a PR with no linked issue and no backport marker stays unattributed', async () => {
  const out = await processPr(fakeResolver(), prInput({ body: 'no refs here' }), { gateRequiredAt: null });
  assert.equal(out.attribution.source, 'unattributed');
});

test('a backport bot PR with only a marker hops to the original and inherits its section attribution + type', async () => {
  const resolver = fakeResolver({
    200: { body: '## Related issues\ncloses #100', title: 'fix: correct retry backoff' },
  });
  const out = await processPr(
    resolver,
    prInput({
      number: 300,
      title: '[Backport 8.8] chore stuff',
      body: 'Backport of #200',
      authorLogin: 'monorepo-devops-automation[bot]',
    }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'section');
  assert.equal(out.attribution.deliveryPath, 'backportHop');
  assert.deepEqual(out.attribution.issueNumbers, [100]);
  assert.equal(out.categorization.section, 'Bug Fixes');
});

test('renovate[bot] with no linked issue is exempt, not unattributed — mirrors the gate\'s BOT_LINK_EXEMPT', async () => {
  const out = await processPr(
    fakeResolver(),
    prInput({ number: 500, title: 'deps: bump foo to 1.2.3', body: 'Bumps foo from 1.2.2 to 1.2.3.', authorLogin: 'renovate[bot]' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'botExempt');
});

test('an exempt bot author with a REAL linked issue keeps that real attribution, exemption never overrides an actual link', async () => {
  const out = await processPr(
    fakeResolver(),
    prInput({ number: 501, title: 'deps: bump foo', body: '## Related issues\ncloses #100', authorLogin: 'renovate[bot]' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'section');
  assert.deepEqual(out.attribution.issueNumbers, [100]);
});

test('a non-exempt author with no linked issue still lands in the unattributed bucket', async () => {
  const out = await processPr(
    fakeResolver(),
    prInput({ number: 502, title: 'ci: bump runner', body: 'no refs here', authorLogin: 'dependabot[bot]' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'unattributed');
});

test('a post-gate PR that falls back to the legacy scan is flagged as an anomaly', async () => {
  const out = await processPr(
    fakeResolver(),
    prInput({ body: 'no section but the prose says fixes #100' }),
    { gateRequiredAt: '2026-07-01T00:00:00Z' },
  );
  assert.equal(out.attribution.source, 'legacyBodyScan');
  assert.equal(out.anomaly, 'post_gate_fallback_attribution');
});
