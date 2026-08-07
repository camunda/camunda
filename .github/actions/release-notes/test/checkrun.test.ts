import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  CHECK_RUN_NAME,
  type CheckRunApi,
  type CheckRunSummary,
  type ExistingCheckRun,
  renderCheckRun,
  syncCheckRun,
} from '../src/checkrun';
import type { GateOutcome } from '../src/types';

const HEAD_SHA = 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678';

const FAIL: GateOutcome = {
  outcome: 'fail',
  deliveryPath: 'direct',
  checks: [
    { label: 'PR-issue link', outcome: 'fail', reasons: ['No linked issue found.'] },
    { label: 'Title', outcome: 'fail', reasons: ['Scopes are not used in this repo.'] },
  ],
  link: { outcome: 'fail', code: 'unlinked-undeclared', reasons: ['No linked issue found.'] },
};
const PASS: GateOutcome = {
  outcome: 'pass',
  deliveryPath: 'direct',
  checks: [{ label: 'PR-issue link', outcome: 'pass', reasons: ['Linked to issue #1234.'] }],
  link: { outcome: 'pass', code: 'section-closing', reasons: ['Linked to issue #1234.'] },
};

/** Records every call so a test can assert what the sync did. */
class FakeApi implements CheckRunApi {
  readonly created: { headSha: string; run: CheckRunSummary }[] = [];
  readonly updated: { id: number; run: CheckRunSummary }[] = [];
  constructor(private readonly existing: ExistingCheckRun[] = []) {}
  async list(): Promise<ExistingCheckRun[]> {
    return this.existing;
  }
  async create(headSha: string, run: CheckRunSummary): Promise<void> {
    this.created.push({ headSha, run });
  }
  async update(id: number, run: CheckRunSummary): Promise<void> {
    this.updated.push({ id, run });
  }
}

test('should report success when every check passes', () => {
  // given / when
  const run = renderCheckRun(PASS, false);

  // then
  assert.equal(run.conclusion, 'success');
  assert.match(run.summary, /Linked to issue #1234/);
});

test('should report failure while warn-only so the rollout surfaces false positives', () => {
  // given a failing gate, with enforcement still off
  // when
  const run = renderCheckRun(FAIL, false);

  // then the check is red even though it blocks nobody
  assert.equal(run.conclusion, 'failure');
});

test('should state that the check is advisory while enforcement is off', () => {
  // given / when
  const run = renderCheckRun(FAIL, false);

  // then the author is told plainly that a red check does not block them
  assert.match(run.title, /does NOT block merging/);
  assert.match(run.summary, /not required/i);
  assert.match(run.summary, /can merge with it red/i);
});

test('should drop the advisory wording once enforcement is on', () => {
  // given / when
  const run = renderCheckRun(FAIL, true);

  // then nothing claims the check is advisory, because it now blocks
  assert.equal(run.conclusion, 'failure');
  assert.doesNotMatch(run.title, /Advisory/);
  assert.doesNotMatch(run.summary, /not required/i);
});

test('should name every failing check in the summary', () => {
  // given / when
  const run = renderCheckRun(FAIL, false);

  // then
  assert.match(run.summary, /PR-issue link/);
  assert.match(run.summary, /Title/);
  assert.match(run.summary, /Scopes are not used/);
});

test('should create a check run when the head SHA has none', async () => {
  // given
  const api = new FakeApi();

  // when
  const action = await syncCheckRun(api, HEAD_SHA, FAIL, false);

  // then
  assert.equal(action, 'created');
  assert.equal(api.created.length, 1);
  assert.equal(api.created[0]!.headSha, HEAD_SHA);
  assert.equal(api.updated.length, 0);
});

test('should update in place rather than stacking a second row on re-run', async () => {
  // given a check run this gate already published for the same commit
  const api = new FakeApi([{ id: 42, name: CHECK_RUN_NAME }]);

  // when the gate runs again (a body edit, say)
  const action = await syncCheckRun(api, HEAD_SHA, FAIL, false);

  // then the existing row is reused — an append would show duplicates on the PR
  assert.equal(action, 'updated');
  assert.equal(api.updated.length, 1);
  assert.equal(api.updated[0]!.id, 42);
  assert.equal(api.created.length, 0);
});

test('should ignore check runs published by other workflows', async () => {
  // given another workflow's check on the same commit
  const api = new FakeApi([{ id: 7, name: 'CI / unit-tests' }]);

  // when
  const action = await syncCheckRun(api, HEAD_SHA, FAIL, false);

  // then the gate publishes its own instead of hijacking someone else's
  assert.equal(action, 'created');
  assert.equal(api.updated.length, 0);
});

test('should flip an existing failed check run to success once the PR is fixed', async () => {
  // given the gate previously failed this commit
  const api = new FakeApi([{ id: 42, name: CHECK_RUN_NAME }]);

  // when it now passes
  await syncCheckRun(api, HEAD_SHA, PASS, false);

  // then
  assert.equal(api.updated[0]!.run.conclusion, 'success');
});
