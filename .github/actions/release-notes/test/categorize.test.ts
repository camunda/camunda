import assert from 'node:assert/strict';
import { test } from 'node:test';
import { categorize, formatDependencyUpdates, hiddenFromCustomerBody, internalIssueKind, parseDependencyUpdate, stripBackportPrefix } from '../src/categorize';

test('every conventional type maps to its designed section and visibility', () => {
  const cases: [string, string | null, 'customer' | 'internal'][] = [
    ['feat', 'Features', 'customer'],
    ['fix', 'Bug Fixes', 'customer'],
    ['perf', 'Performance', 'customer'],
    ['docs', 'Documentation', 'customer'],
    ['deps', 'Dependency updates', 'customer'],
    ['revert', 'Reverts', 'customer'],
    ['refactor', 'Maintenance', 'internal'],
    ['build', 'Maintenance', 'internal'],
    ['ci', 'Maintenance', 'internal'],
    ['test', 'Maintenance', 'internal'],
    ['style', 'Maintenance', 'internal'],
    ['merge', null, 'customer'], // excluded from both outputs, D25
  ];
  for (const [type, section, visibility] of cases) {
    const d = categorize({ title: `${type}: something`, componentLabels: [], breakingChangeLabel: false });
    assert.equal(d.section, section, `${type} -> section`);
    assert.equal(d.visibility, visibility, `${type} -> visibility`);
  }
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

test('parseDependencyUpdate reads name/old/new from a renovate single-row table', () => {
  const body = [
    'This PR contains the following updates:',
    '',
    '| Package | Change | [Age](https://x) | [Confidence](https://x) |',
    '|---|---|---|---|',
    '| [org.liquibase:liquibase-core](http://www.liquibase.com) ([source](https://x)) | `5.0.3` → `5.0.4` | ![age](x) | ![confidence](x) |',
  ].join('\n');
  const result = parseDependencyUpdate({ title: 'deps: Update dependency org.liquibase:liquibase-core to v5.0.4 (stable/8.9)', body });
  assert.deepEqual(result, [{ name: 'org.liquibase:liquibase-core', from: '5.0.3', to: '5.0.4' }]);
});

test('parseDependencyUpdate reads a Docker-tag renovate row with an extra Update column', () => {
  const body = [
    '| Package | Update | Change |',
    '|---|---|---|',
    '| [camunda/camunda](https://camunda.com/platform/) ([source](https://x)) | patch | `8.8.35` → `8.8.36` |',
  ].join('\n');
  const result = parseDependencyUpdate({ title: 'deps: Update camunda/camunda Docker tag to v8.8.36 (stable/8.8)', body });
  assert.deepEqual(result, [{ name: 'camunda/camunda', from: '8.8.35', to: '8.8.36' }]);
});

test('parseDependencyUpdate returns every row of a grouped renovate PR', () => {
  const body = [
    '| Package | Change |',
    '|---|---|',
    '| [org.springframework.boot:spring-boot](https://x) | `4.1.0` → `4.1.1` |',
    '| [org.springframework.boot:spring-boot](https://x) | `4.0.7` → `4.0.8` |',
  ].join('\n');
  const result = parseDependencyUpdate({ title: 'deps: Update spring boot (stable/8.8) (patch)', body });
  assert.deepEqual(result, [
    { name: 'org.springframework.boot:spring-boot', from: '4.1.0', to: '4.1.1' },
    { name: 'org.springframework.boot:spring-boot', from: '4.0.7', to: '4.0.8' },
  ]);
  // The title still reads as one line; the renderer is what collapses them.
  assert.equal(
    formatDependencyUpdates(result),
    'org.springframework.boot:spring-boot: 4.1.0 → 4.1.1; org.springframework.boot:spring-boot: 4.0.7 → 4.0.8',
  );
});

test('parseDependencyUpdate reads a dependabot "Bump X from A to B" title directly, no body needed', () => {
  const result = parseDependencyUpdate({ title: 'Bump foo from 1.2.2 to 1.2.3', body: 'Bumps foo from 1.2.2 to 1.2.3.' });
  assert.deepEqual(result, [{ name: 'foo', from: '1.2.2', to: '1.2.3' }]);
});

test('parseDependencyUpdate returns nothing when neither shape matches', () => {
  assert.deepEqual(parseDependencyUpdate({ title: 'ci: bump runner', body: 'no table here' }), []);
});

test('a kind/task issue is never customer-facing, whatever the pull request type says', () => {
  // given — 8.9.19 shipped "CamundaSpringProcessTestConnectorsIT is flaky" to
  // customers under Bug Fixes, because the delivering pull request was a `fix:`
  // while the issue itself is a task
  const labels = ['kind/task', 'component/camunda-process-test', 'version:8.9.19'];

  // when
  const kind = internalIssueKind(labels);

  // then — named, not just flagged, so the audit line can say which label did it
  assert.equal(kind, 'kind/task');
});

test('a kind/epic issue is never customer-facing either', () => {
  // given — "[EPIC]: Prepare 8.10 release for load testing" reached 8.9.19's
  // customer body under Documentation, via a `docs:` pull request
  // when
  const kind = internalIssueKind(['kind/epic', 'component/load-tests']);

  // then
  assert.equal(kind, 'kind/epic');
});

test('a kind/bug issue stays customer-facing', () => {
  // when
  const kind = internalIssueKind(['kind/bug', 'severity/mid', 'component/zeebe']);

  // then
  assert.equal(kind, null);
});

test('an issue with no kind label at all stays customer-facing', () => {
  // then — absence is not a reason to hide delivered work
  assert.equal(internalIssueKind([]), null);
  assert.equal(internalIssueKind(['component/zeebe']), null);
});

test('an entry is hidden only when EVERY linked issue is internal', () => {
  // given — 8.9.19's #61857 closed kind/bug #61719 AND kind/task #56995. The
  // first attempt hid on any internal label and suppressed a real customer bug.
  const mixed = [['kind/bug', 'component/zeebe'], ['kind/task', 'component/qa']];

  // when
  const hidden = hiddenFromCustomerBody(mixed);

  // then
  assert.equal(hidden, null);
});

test('an entry whose every linked issue is internal is hidden, and names the kind', () => {
  // when
  const hidden = hiddenFromCustomerBody([['kind/task'], ['kind/epic']]);

  // then
  assert.equal(hidden, 'kind/task');
});

test('a pull request linking no issue at all is not hidden — absence is not a signal', () => {
  // then — an opt-out or bot-exempt PR keeps its place
  assert.equal(hiddenFromCustomerBody([]), null);
});
