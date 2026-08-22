import assert from 'node:assert/strict';
import { test } from 'node:test';
import { evaluateGate, type GateResolver } from '../src/gate';
import type { ParsedRef, RefTarget, ResolvedRef } from '../src/types';

/** Fake resolver: classify by a number→target map; serve PR bodies by number. */
class FakeResolver implements GateResolver {
  constructor(
    private readonly targets: Record<number, RefTarget>,
    private readonly bodies: Record<number, string> = {},
  ) {}
  async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
    return refs.map((ref) => ({
      ...ref,
      target: this.targets[ref.number] ?? 'missing',
      crossRepo: ref.repo !== null && ref.repo.toLowerCase() !== 'camunda/camunda',
    }));
  }
  async fetchPullBody(number: number, repo: string | null): Promise<string | null> {
    const crossRepo = repo !== null && repo.toLowerCase() !== 'camunda/camunda';
    if (crossRepo) return null; // resolver only validates its own repo
    return number in this.bodies ? (this.bodies[number] ?? '') : null;
  }
}

const withSection = (refLine: string) => `## Related issues\n\n${refLine}\n`;
const BOT_BACKPORT = '⤵️ Backport of #500 → `stable/8.8`\n\nrelates to #10\n';

test('direct PR with a live issue in the section passes', async () => {
  const resolver = new FakeResolver({ 10: 'issue' });
  const gate = await evaluateGate(resolver, { body: withSection('closes #10'), title: 'fix: x', authorLogin: 'szpraat' });
  assert.equal(gate.outcome, 'pass');
  assert.equal(gate.deliveryPath, 'direct');
});

test('bot backport with no section passes by inheriting the original PR attribution', async () => {
  const resolver = new FakeResolver({ 10: 'issue', 500: 'pullRequest' }, { 500: withSection('closes #10') });
  const gate = await evaluateGate(resolver, { body: BOT_BACKPORT, title: 'anything', authorLogin: 'backport-action' });
  assert.equal(gate.outcome, 'pass');
  assert.equal(gate.deliveryPath, 'backportHop');
  // bot author → title check is skipped, so only the link check is present
  assert.deepEqual(gate.checks.map((check) => check.label), ['PR-issue link']);
});

test('backport fails when the original PR is also unlinked — names the original', async () => {
  const resolver = new FakeResolver({ 500: 'pullRequest' }, { 500: 'no related-issues section here' });
  const gate = await evaluateGate(resolver, { body: BOT_BACKPORT, title: 'x', authorLogin: 'backport-action' });
  assert.equal(gate.outcome, 'fail');
  assert.equal(gate.deliveryPath, 'backportHop');
  assert.ok(gate.checks[0]?.reasons.some((reason) => reason.includes('#500')));
});

test('a valid link but a bad title fails the gate (both checks reported)', async () => {
  const resolver = new FakeResolver({ 10: 'issue' });
  const gate = await evaluateGate(resolver, {
    body: withSection('closes #10'),
    title: 'no type here',
    authorLogin: 'szpraat',
  });
  assert.equal(gate.outcome, 'fail');
  assert.deepEqual(gate.checks.map((check) => `${check.label}:${check.outcome}`), ['PR-issue link:pass', 'Title:fail']);
});

test('bot authors skip title lint — a valid link alone passes', async () => {
  const resolver = new FakeResolver({ 10: 'issue' });
  const gate = await evaluateGate(resolver, {
    body: withSection('closes #10'),
    title: 'this would fail title lint',
    authorLogin: 'renovate[bot]',
  });
  assert.equal(gate.outcome, 'pass');
  assert.equal(gate.checks.length, 1);
});

test('a PR ref in the section is NOT rescued by an unrelated Backport marker', async () => {
  // #10 is a PR (hard error pr-ref-in-section); #500's original links a live issue.
  // The hop must not fire — the section-level PR ref stands as a fail.
  const resolver = new FakeResolver({ 10: 'pullRequest', 20: 'issue', 500: 'pullRequest' }, { 500: withSection('closes #20') });
  const body = `${withSection('closes #10')}\n⤵️ Backport of #500\n`;
  const gate = await evaluateGate(resolver, { body, title: 'fix: x', authorLogin: 'szpraat' });
  assert.equal(gate.outcome, 'fail');
  assert.equal(gate.deliveryPath, 'direct'); // no hop happened
  assert.ok(gate.checks[0]?.reasons.some((reason) => reason.includes('pull request')));
});

test('a cross-repo Backport marker cannot inherit attribution — says so explicitly', async () => {
  const resolver = new FakeResolver({ 10: 'issue' }, { 500: withSection('closes #10') });
  const gate = await evaluateGate(resolver, {
    body: '⤵️ Backport of other-org/other-repo#500\n',
    title: 'fix: x',
    authorLogin: 'szpraat',
  });
  assert.equal(gate.outcome, 'fail');
  assert.equal(gate.deliveryPath, 'backportHop');
  assert.ok(gate.checks[0]?.reasons.some((reason) => reason.includes('another repository')));
});

test('a Backport marker pointing at an issue (not a PR) says exactly that', async () => {
  // Mirrors gate-test-07: `Backport of #53593` where #53593 is an issue.
  const resolver = new FakeResolver({ 500: 'issue' }, {}); // resolves as issue; no PR body
  const gate = await evaluateGate(resolver, { body: '⤵️ Backport of #500\n', title: 'fix: x', authorLogin: 'szpraat' });
  assert.equal(gate.outcome, 'fail');
  assert.equal(gate.deliveryPath, 'backportHop');
  assert.ok(gate.checks[0]?.reasons.some((reason) => reason.includes('#500') && reason.includes('issue, not a pull request')));
});

test('a dangling Backport target (missing) is surfaced, not absorbed into a generic message', async () => {
  const resolver = new FakeResolver({}, {}); // #500 neither a PR body nor a known target → missing
  const gate = await evaluateGate(resolver, { body: '⤵️ Backport of #500\n', title: 'fix: x', authorLogin: 'szpraat' });
  assert.equal(gate.outcome, 'fail');
  assert.equal(gate.deliveryPath, 'backportHop');
  assert.ok(gate.checks[0]?.reasons.some((reason) => reason.includes('#500') && reason.includes('does not resolve to a pull request')));
});

test('an unlinked Renovate PR passes via the bot link exemption', async () => {
  const resolver = new FakeResolver({});
  const gate = await evaluateGate(resolver, {
    body: 'This PR updates a dependency.\n',
    title: 'deps: Update dependency zstd-jni to v1.5.7-13',
    authorLogin: 'renovate[bot]',
  });
  assert.equal(gate.outcome, 'pass');
  assert.equal(gate.link.code, 'bot-exempt');
});

test('the exemption is a fallback — a Renovate PR that does link an issue keeps its real code', async () => {
  const resolver = new FakeResolver({ 10: 'issue' });
  const gate = await evaluateGate(resolver, {
    body: withSection('closes #10'),
    title: 'deps: Update dependency zstd-jni to v1.5.7-13',
    authorLogin: 'renovate[bot]',
  });
  assert.equal(gate.outcome, 'pass');
  assert.equal(gate.link.code, 'section-closing');
});

test('the backport bot is NOT link-exempt — an unlinked backport still fails', async () => {
  // Guards the reason BOT_LINK_EXEMPT is separate from BOT_TITLE_EXEMPT: the
  // backport author must keep going through the hop, so a backport whose
  // original is also unlinked stays a failure instead of being exempted away.
  const resolver = new FakeResolver({ 500: 'pullRequest' }, { 500: 'no related-issues section here' });
  const gate = await evaluateGate(resolver, {
    body: BOT_BACKPORT,
    title: 'fix: x',
    authorLogin: 'monorepo-devops-automation[bot]',
  });
  assert.equal(gate.outcome, 'fail');
  assert.equal(gate.deliveryPath, 'backportHop');
  assert.notEqual(gate.link.code, 'bot-exempt');
});
