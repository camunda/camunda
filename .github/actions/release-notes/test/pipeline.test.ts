import assert from 'node:assert/strict';
import { test } from 'node:test';
import { processPr } from '../src/pipeline';
import type { PipelinePrInput, PipelineResolver } from '../src/pipeline';
import type { ParsedRef, ResolvedRef } from '../src/types';

/** A fake resolver: classifies every ref number 100 as a live issue, 999 as missing. */
function fakeResolver(
  originals: Record<number, { body: string; title: string; authorLogin?: string; mergedAt?: string }> = {},
  issueTitles: Record<number, string> = {},
  counts: { originalPulls: number; resolveCalls: number } = { originalPulls: 0, resolveCalls: 0 },
): PipelineResolver {
  return {
    async resolveRefs(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
      counts.resolveCalls++;
      return refs.map((ref) => ({ ...ref, target: ref.number === 999 ? 'missing' : 'issue', crossRepo: false }));
    },
    async fetchOriginalPull(number: number, repo: string | null) {
      counts.originalPulls++;
      // Mirrors GithubResolver.fetchOriginalPull: a cross-repo marker never
      // resolves to a same-numbered PR in this repo.
      if (repo !== null) return null;
      return originals[number] ?? null;
    },
    async fetchIssueTitle(number: number) {
      return issueTitles[number] ?? null;
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

test('an attributed PR displays the ISSUE title, not the PR title — the issue is written for a release-notes reader', async () => {
  const out = await processPr(
    fakeResolver({}, { 100: 'Batch delete silently drops rows over 500' }),
    prInput({ title: 'feat: add batch delete API' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.title, 'Batch delete silently drops rows over 500');
});


test('a renovate dependency-update PR shows "name: old -> new", never its own title or the issue title', async () => {
  const body = [
    '| Package | Change |',
    '|---|---|',
    '| [org.liquibase:liquibase-core](https://x) | `5.0.3` → `5.0.4` |',
  ].join('\n');
  const out = await processPr(
    fakeResolver({}, { 100: 'should never be shown for a deps PR' }),
    prInput({
      title: 'deps: Update dependency org.liquibase:liquibase-core to v5.0.4 (stable/8.9)',
      body,
      authorLogin: 'renovate[bot]',
    }),
    { gateRequiredAt: null },
  );
  assert.equal(out.title, 'org.liquibase:liquibase-core: 5.0.3 → 5.0.4');
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

test('a PR with no linked issue and no backport marker stays unattributed and keeps its own title', async () => {
  const out = await processPr(fakeResolver(), prInput({ body: 'no refs here' }), { gateRequiredAt: null });
  assert.equal(out.attribution.source, 'unattributed');
  assert.equal(out.title, 'feat: add batch delete API');
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

test('the backport hop and the inherit-original title share ONE fetch of the original PR', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const resolver = fakeResolver(
    { 200: { body: '## Related issues\ncloses #100', title: 'fix: correct retry backoff' } },
    {},
    counts,
  );
  const out = await processPr(
    resolver,
    prInput({ number: 300, title: 'chore stuff', body: 'Backport of #200', authorLogin: 'monorepo-devops-automation[bot]' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.deliveryPath, 'backportHop');
  assert.equal(out.title, 'fix: correct retry backoff');
  assert.equal(counts.originalPulls, 1, 'attribution and title resolution must reuse the same lookup');
});

test('a PR with no backport marker never fetches an original PR at all', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const out = await processPr(fakeResolver({}, {}, counts), prInput({ body: 'no refs here' }), { gateRequiredAt: null });
  assert.equal(out.attribution.source, 'unattributed');
  assert.equal(counts.originalPulls, 0);
});

test('a post-gate backport of a PRE-gate original is not an anomaly — the rule keys on the original\'s merge', async () => {
  const resolver = fakeResolver({ 200: { body: 'fixes #100', title: 'fix: correct retry backoff', mergedAt: '2026-06-01T00:00:00Z' } });
  const out = await processPr(
    resolver,
    prInput({ number: 300, body: 'Backport of #200', mergedAt: '2026-08-01T00:00:00Z' }),
    { gateRequiredAt: '2026-07-01T00:00:00Z' },
  );
  assert.equal(out.attribution.source, 'legacyBodyScan');
  assert.equal(out.attribution.deliveryPath, 'backportHop');
  assert.equal(out.anomaly, undefined);
});

test('a post-gate backport of a POST-gate original still flags the anomaly', async () => {
  const resolver = fakeResolver({ 200: { body: 'fixes #100', title: 'fix: correct retry backoff', mergedAt: '2026-07-15T00:00:00Z' } });
  const out = await processPr(
    resolver,
    prInput({ number: 301, body: 'Backport of #200', mergedAt: '2026-08-01T00:00:00Z' }),
    { gateRequiredAt: '2026-07-01T00:00:00Z' },
  );
  assert.equal(out.anomaly, 'post_gate_fallback_attribution');
});

test('an original with no merge timestamp falls back to the backport\'s own, never skipping the check', async () => {
  const resolver = fakeResolver({ 200: { body: 'fixes #100', title: 'fix: correct retry backoff' } });
  const out = await processPr(
    resolver,
    prInput({ number: 302, body: 'Backport of #200', mergedAt: '2026-08-01T00:00:00Z' }),
    { gateRequiredAt: '2026-07-01T00:00:00Z' },
  );
  assert.equal(out.anomaly, 'post_gate_fallback_attribution');
});

test('a section-attributed PR never resolves the body-wide legacy scan — its refs would be resolved twice', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const out = await processPr(fakeResolver({}, {}, counts), prInput(), { gateRequiredAt: null });
  assert.equal(out.attribution.source, 'section');
  assert.equal(counts.resolveCalls, 1, 'only the section scan should reach the resolver');
});

test('a natively-attributed PR (no section) never resolves the legacy scan either', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const out = await processPr(
    fakeResolver({}, {}, counts),
    prInput({ body: 'no refs in the body at all', closingIssuesReferences: [100] }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'closingIssuesReferences');
  assert.equal(counts.resolveCalls, 0);
});

test('an opt-out PR never resolves the legacy scan', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const out = await processPr(
    fakeResolver({}, {}, counts),
    prInput({ body: '## Related issues\n- [x] This PR does not need a linked issue\n\nsee also #100' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'optOut');
  assert.equal(counts.resolveCalls, 1, 'the section is still scanned, the body is not');
});

test('a PR whose only ref is outside the section still reaches the legacy scan', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const out = await processPr(
    fakeResolver({}, {}, counts),
    prInput({ body: 'the prose says fixes #100' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'legacyBodyScan');
  assert.deepEqual(out.attribution.issueNumbers, [100]);
});

test('a cross-repo backport marker never inherits a same-numbered PR from this repo', async () => {
  const resolver = fakeResolver({
    // Same number as the cross-repo marker below — must NOT be picked up.
    200: { body: '## Related issues\ncloses #100', title: 'fix: unrelated local PR' },
  });
  const out = await processPr(
    resolver,
    prInput({ number: 300, title: 'chore stuff', body: 'Backport of camunda/other-repo#200', authorLogin: 'monorepo-devops-automation[bot]' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'unattributed');
  assert.equal(out.title, 'chore stuff');
});

test('a resolutionFailed direct attribution still hops to the backport original, same as unattributed', async () => {
  const resolver = fakeResolver({
    200: { body: '## Related issues\ncloses #100', title: 'fix: correct retry backoff' },
  });
  const out = await processPr(
    resolver,
    prInput({
      number: 300,
      title: 'chore stuff',
      body: '## Related issues\ncloses #999\n\nBackport of #200',
      authorLogin: 'monorepo-devops-automation[bot]',
    }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'section');
  assert.equal(out.attribution.deliveryPath, 'backportHop');
  assert.deepEqual(out.attribution.issueNumbers, [100]);
});

test('a section holding only a dead ref reports resolutionFailed and does NOT fall through to the legacy scan', async () => {
  const counts = { originalPulls: 0, resolveCalls: 0 };
  const out = await processPr(
    fakeResolver({}, {}, counts),
    prInput({ body: '## Related issues\ncloses #999\n\n## Notes\nalso mentions #100' }),
    { gateRequiredAt: null },
  );
  assert.equal(out.attribution.source, 'resolutionFailed');
  assert.equal(counts.resolveCalls, 1);
});
