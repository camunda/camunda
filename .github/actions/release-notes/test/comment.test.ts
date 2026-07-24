import assert from 'node:assert/strict';
import { test } from 'node:test';
import { type CommentApi, GithubCommentApi, type IssueComment, renderStickyComment, STICKY_MARKER, syncStickyComment } from '../src/comment';
import type { GateOutcome } from '../src/types';

const FAIL: GateOutcome = {
  outcome: 'fail',
  deliveryPath: 'direct',
  checks: [{ label: 'PR-issue link', outcome: 'fail', reasons: ['No linked issue found.', 'Add "closes #1234".'] }],
  link: { outcome: 'fail', code: 'unlinked-undeclared', reasons: ['No linked issue found.', 'Add "closes #1234".'] },
};
const PASS: GateOutcome = {
  outcome: 'pass',
  deliveryPath: 'direct',
  checks: [{ label: 'PR-issue link', outcome: 'pass', reasons: ['Linked to issue #1234.'] }],
  link: { outcome: 'pass', code: 'section-closing', reasons: ['Linked to issue #1234.'] },
};

/** Records every call so a test can assert what the sync did. */
class FakeApi implements CommentApi {
  readonly created: string[] = [];
  readonly updated: { id: number; body: string }[] = [];
  constructor(private readonly comments: IssueComment[] = []) {}
  async list(): Promise<IssueComment[]> {
    return this.comments;
  }
  async create(body: string): Promise<void> {
    this.created.push(body);
  }
  async update(id: number, body: string): Promise<void> {
    this.updated.push({ id, body });
  }
}

test('renderStickyComment always carries the marker and the reasons', () => {
  const body = renderStickyComment(FAIL);
  assert.ok(body.startsWith(STICKY_MARKER));
  assert.ok(body.includes('No linked issue found.'));
});

test('fail with no existing comment creates one', async () => {
  const api = new FakeApi([]);
  const action = await syncStickyComment(api, FAIL);
  assert.equal(action, 'created');
  assert.equal(api.created.length, 1);
  assert.equal(api.updated.length, 0);
  assert.ok(api.created[0]?.includes(STICKY_MARKER));
});

test('fail with an existing marked comment updates it — no duplicate', async () => {
  const api = new FakeApi([{ id: 42, body: `${STICKY_MARKER}\nold` }]);
  const action = await syncStickyComment(api, FAIL);
  assert.equal(action, 'updated');
  assert.equal(api.created.length, 0);
  assert.deepEqual(api.updated.map((call) => call.id), [42]);
});

test('pass with an existing marked comment flips it to resolved', async () => {
  const api = new FakeApi([{ id: 7, body: `${STICKY_MARKER}\nfailure` }]);
  const action = await syncStickyComment(api, PASS);
  assert.equal(action, 'resolved');
  assert.equal(api.created.length, 0);
  assert.equal(api.updated[0]?.id, 7);
  assert.ok(api.updated[0]?.body.includes('passed'));
});

test('pass with no existing comment does nothing — never-failed PRs stay clean', async () => {
  const api = new FakeApi([{ id: 1, body: 'an unrelated human comment' }]);
  const action = await syncStickyComment(api, PASS);
  assert.equal(action, 'noop');
  assert.equal(api.created.length, 0);
  assert.equal(api.updated.length, 0);
});

test('unmarked comments are ignored when locating the sticky comment', async () => {
  const api = new FakeApi([{ id: 1, body: 'unrelated' }, { id: 2, body: 'also unrelated' }]);
  const action = await syncStickyComment(api, FAIL);
  assert.equal(action, 'created');
  assert.equal(api.updated.length, 0);
});

test('GithubCommentApi.list paginates so a sticky past page 1 is still found', async () => {
  // A busy PR: page 1 is 100 unrelated comments, the sticky is comment #101.
  const page1: IssueComment[] = Array.from({ length: 100 }, (_, i) => ({ id: i + 1, body: `noise ${i}` }));
  const page2: IssueComment[] = [{ id: 101, body: `${STICKY_MARKER}\nfailure` }];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (url: string) => {
    const body = new URL(url).searchParams.get('page') === '2' ? page2 : page1;
    return { ok: true, status: 200, json: async () => body } as Response;
  }) as typeof fetch;
  try {
    const api = new GithubCommentApi('tok', 'camunda', 'camunda', 42);
    const all = await api.list();
    assert.equal(all.length, 101);
    assert.ok(all.some((comment) => comment.body.includes(STICKY_MARKER)));
  } finally {
    globalThis.fetch = originalFetch;
  }
});
