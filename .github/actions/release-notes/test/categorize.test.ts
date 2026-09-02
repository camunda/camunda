import assert from 'node:assert/strict';
import { test } from 'node:test';
import { categorize, resolveCategorizeTitle, stripBackportPrefix } from '../src/categorize';

test('conventional type maps to its section: feat -> Features', () => {
  const d = categorize({ title: 'feat: add batch delete API', componentLabels: [], breakingChangeLabel: false });
  assert.equal(d.section, 'Features');
  assert.equal(d.visibility, 'customer');
});

test('fix maps to Bug Fixes', () => {
  const d = categorize({ title: 'fix: correct retry backoff', componentLabels: [], breakingChangeLabel: false });
  assert.equal(d.section, 'Bug Fixes');
});

test('stripBackportPrefix removes a leading [Backport ...] marker — it is noise, not a customer-facing fact', () => {
  assert.equal(
    stripBackportPrefix('[Backport stable/8.9] fix: keep job dispatch from blocking the client\'s transport thread'),
    'fix: keep job dispatch from blocking the client\'s transport thread',
  );
  assert.equal(stripBackportPrefix('[Backport 8.8] chore stuff'), 'chore stuff');
});

test('stripBackportPrefix leaves an unrelated leading bracket untouched', () => {
  assert.equal(stripBackportPrefix('[CPT] isCompleted() fails when asserted seconds later'), '[CPT] isCompleted() fails when asserted seconds later');
  assert.equal(stripBackportPrefix('[Doc Handling] Azure Blob Storage document store implementation'), '[Doc Handling] Azure Blob Storage document store implementation');
});

test('stripBackportPrefix is a no-op on a title with no bracket prefix', () => {
  assert.equal(stripBackportPrefix('feat: add batch delete API'), 'feat: add batch delete API');
});

test('resolveCategorizeTitle: a backport bot inherits the original PR title, not its own', () => {
  const title = resolveCategorizeTitle({
    title: '[Backport 8.8] chore stuff',
    authorLogin: 'monorepo-devops-automation[bot]',
    originalTitle: 'fix: correct retry backoff',
  });
  const d = categorize({ title, componentLabels: [], breakingChangeLabel: false });
  assert.equal(d.section, 'Bug Fixes');
});

test('resolveCategorizeTitle: a non-inherit-original bot keeps its own title', () => {
  const title = resolveCategorizeTitle({ title: 'feat: something', authorLogin: 'qa-processes[bot]' });
  assert.equal(title, 'feat: something');
});

test('D27: unknown bot with a parseable title categorizes via the plain fallback, no map entry needed', () => {
  const d = categorize({
    title: 'test: gate decision-instance batch delete',
    authorLogin: 'qa-processes[bot]',
    componentLabels: [],
    breakingChangeLabel: false,
  });
  assert.equal(d.section, 'Maintenance');
  assert.equal(d.visibility, 'internal');
  assert.deepEqual(d.reasons, []);
});

test('unknown bot with an unparseable title -> Uncategorized, audited by login (C10: never silently drop)', () => {
  const d = categorize({
    title: 'Automated update',
    authorLogin: 'some-random-bot[bot]',
    componentLabels: [],
    breakingChangeLabel: false,
  });
  assert.equal(d.section, 'Uncategorized');
  assert.equal(d.visibility, 'customer');
  assert.ok(d.reasons.some((r) => r.includes('some-random-bot[bot]')));
});

test('multi-component PR groups under "Multiple components", both named in the audit', () => {
  const d = categorize({
    title: 'feat: add batch delete API',
    componentLabels: ['component/zeebe', 'component/operate'],
    breakingChangeLabel: false,
  });
  assert.equal(d.component, 'Multiple components');
  assert.ok(d.reasons.some((r) => r.includes('component/zeebe') && r.includes('component/operate')));
});

test('a single component label is used as-is', () => {
  const d = categorize({ title: 'feat: x', componentLabels: ['component/zeebe'], breakingChangeLabel: false });
  assert.equal(d.component, 'component/zeebe');
});

test('BREAKING CHANGE label flags breaking in addition to the normal section, not instead of it', () => {
  const d = categorize({ title: 'feat: x', componentLabels: [], breakingChangeLabel: true });
  assert.equal(d.section, 'Features');
  assert.equal(d.breaking, true);
});

test('renovate[bot] is forced to deps regardless of its own title', () => {
  const d = categorize({
    title: 'chore(deps): bump foo to 1.2.3',
    authorLogin: 'renovate[bot]',
    componentLabels: [],
    breakingChangeLabel: false,
  });
  assert.equal(d.section, 'Dependency updates');
});

test('dependabot[bot] is forced to deps regardless of its own title', () => {
  const d = categorize({
    title: 'Bump foo from 1 to 2',
    authorLogin: 'dependabot[bot]',
    componentLabels: [],
    breakingChangeLabel: false,
  });
  assert.equal(d.section, 'Dependency updates');
});

test('merge type is excluded entirely, not Uncategorized', () => {
  const d = categorize({ title: 'merge: release-8.8.30 back to stable/8.8', componentLabels: [], breakingChangeLabel: false });
  assert.equal(d.section, null);
});

test('internal-only types (build/ci/test/style/refactor) are asset-only, not customer-visible', () => {
  for (const type of ['build', 'ci', 'test', 'style', 'refactor']) {
    const d = categorize({ title: `${type}: something`, componentLabels: [], breakingChangeLabel: false });
    assert.equal(d.section, 'Maintenance', `${type} should map to Maintenance`);
    assert.equal(d.visibility, 'internal', `${type} should be internal-only`);
  }
});
