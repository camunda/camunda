import assert from 'node:assert/strict';
import { test } from 'node:test';
import { decideLabelAction, type LabelApi, NO_ISSUE_LABEL, syncNoIssueLabel } from '../src/labels';
import type { GateOutcome } from '../src/types';

const LINK_FAIL: GateOutcome = {
  outcome: 'fail',
  deliveryPath: 'direct',
  checks: [{ label: 'PR-issue link', outcome: 'fail', reasons: ['No linked issue found.'] }],
  link: { outcome: 'fail', code: 'unlinked-undeclared', reasons: ['No linked issue found.'] },
};
const LINK_PASS: GateOutcome = {
  outcome: 'pass',
  deliveryPath: 'direct',
  checks: [{ label: 'PR-issue link', outcome: 'pass', reasons: ['Linked to issue #1234.'] }],
  link: { outcome: 'pass', code: 'section-closing', reasons: ['Linked to issue #1234.'] },
};
const TITLE_ONLY_FAIL: GateOutcome = {
  outcome: 'fail',
  deliveryPath: 'direct',
  checks: [
    { label: 'PR-issue link', outcome: 'pass', reasons: ['Linked to issue #1234.'] },
    { label: 'Title', outcome: 'fail', reasons: ['bad title'] },
  ],
  link: { outcome: 'pass', code: 'section-closing', reasons: ['Linked to issue #1234.'] },
};

/** Records every call so a test can assert what the sync did. */
class FakeApi implements LabelApi {
  readonly added: string[] = [];
  readonly removed: string[] = [];
  constructor(private labels: string[] = []) {}
  async list(): Promise<string[]> {
    return this.labels;
  }
  async add(label: string): Promise<void> {
    this.added.push(label);
  }
  async remove(label: string): Promise<void> {
    this.removed.push(label);
  }
}

test('decideLabelAction adds when the link fails and the label is absent', () => {
  assert.equal(decideLabelAction([], 'fail'), 'added');
});

test('decideLabelAction is a noop when the link fails and the label is already present', () => {
  assert.equal(decideLabelAction([NO_ISSUE_LABEL], 'fail'), 'noop');
});

test('decideLabelAction removes when the link passes and the label is present', () => {
  assert.equal(decideLabelAction([NO_ISSUE_LABEL], 'pass'), 'removed');
});

test('decideLabelAction is a noop when the link passes and the label is absent', () => {
  assert.equal(decideLabelAction([], 'pass'), 'noop');
});

test('syncNoIssueLabel adds the label when the link check fails', async () => {
  const api = new FakeApi([]);
  const action = await syncNoIssueLabel(api, LINK_FAIL);
  assert.equal(action, 'added');
  assert.deepEqual(api.added, [NO_ISSUE_LABEL]);
  assert.deepEqual(api.removed, []);
});

test('syncNoIssueLabel removes the label once the link check passes', async () => {
  const api = new FakeApi([NO_ISSUE_LABEL]);
  const action = await syncNoIssueLabel(api, LINK_PASS);
  assert.equal(action, 'removed');
  assert.deepEqual(api.removed, [NO_ISSUE_LABEL]);
});

test('syncNoIssueLabel reflects the link check only — a title-only failure does not add the label', async () => {
  const api = new FakeApi([]);
  const action = await syncNoIssueLabel(api, TITLE_ONLY_FAIL);
  assert.equal(action, 'noop');
  assert.deepEqual(api.added, []);
});

test('syncNoIssueLabel leaves unrelated labels untouched', async () => {
  const api = new FakeApi(['bug', 'priority/high']);
  const action = await syncNoIssueLabel(api, LINK_FAIL);
  assert.equal(action, 'added');
  assert.deepEqual(api.added, [NO_ISSUE_LABEL]);
});

test('syncNoIssueLabel is a noop when already correctly labeled and link still fails', async () => {
  const api = new FakeApi([NO_ISSUE_LABEL]);
  const action = await syncNoIssueLabel(api, LINK_FAIL);
  assert.equal(action, 'noop');
  assert.deepEqual(api.added, []);
  assert.deepEqual(api.removed, []);
});
