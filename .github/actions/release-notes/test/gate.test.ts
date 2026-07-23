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
  async fetchPullBody(number: number): Promise<string | null> {
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
