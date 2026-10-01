import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { test } from 'node:test';
import type { TestContext } from 'node:test';
import type { RenderPrInput } from '../src/render';

interface Pull {
  readonly number: number;
  readonly title: string;
  readonly body?: string;
  readonly bot?: boolean;
  readonly breaking?: boolean;
  readonly closingIssues?: readonly { number: number; repository: { nameWithOwner: string } }[];
}

function generate(t: TestContext, pulls: readonly Pull[], allowUnattributed = true) {
  const directory = mkdtempSync(join(tmpdir(), 'release-notes-generate-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const env = {
    ...process.env,
    GIT_AUTHOR_NAME: 'test',
    GIT_AUTHOR_EMAIL: 'test@example.com',
    GIT_COMMITTER_NAME: 'test',
    GIT_COMMITTER_EMAIL: 'test@example.com',
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_CONFIG_GLOBAL: '/dev/null',
  };
  const git = (...args: string[]) => execFileSync('git', args, { cwd: directory, env, encoding: 'utf8' }).trim();
  git('init', '-q', '-b', 'main');
  git('commit', '-q', '--allow-empty', '-m', 'baseline');
  git('tag', '8.9.18');
  const metadata = pulls.map((pull) => {
    git('commit', '-q', '--allow-empty', '-m', `${pull.title} (#${pull.number})`);
    return {
      number: pull.number,
      title: pull.title,
      body: pull.body ?? '',
      baseRefName: 'main',
      headRefName: `feature-${pull.number}`,
      mergeCommit: { oid: git('rev-parse', 'HEAD') },
      mergedAt: '2026-01-01T00:00:00Z',
      author: { login: pull.bot ? 'renovate' : 'someone', __typename: pull.bot ? 'Bot' : 'User' },
      labels: { nodes: pull.breaking ? [{ name: 'BREAKING CHANGE' }] : [] },
      closingIssuesReferences: { nodes: pull.closingIssues ?? [] },
    };
  });
  git('tag', '8.9.19');

  const preload = join(directory, 'github.cjs');
  const calls = join(directory, 'calls.json');
  writeFileSync(preload, `
const { writeFileSync } = require('node:fs');
const metadata = ${JSON.stringify(metadata)};
const calls = [];
globalThis.fetch = async (url, init) => {
  if (url !== 'https://api.github.com/graphql') throw new Error('Unexpected URL: ' + url);
  const { query, variables } = JSON.parse(init.body);
  calls.push({ query, variables });
  writeFileSync(${JSON.stringify(calls)}, JSON.stringify(calls));
  const repository = {};
  for (const [key, number] of Object.entries(variables)) {
    if (!/^v\\d+$/.test(key)) continue;
    const index = key.slice(1);
    if (query.includes('pullRequest(number:')) {
      repository['pr' + index] = metadata.find(pr => pr.number === number) ?? null;
    } else if (query.includes('issueOrPullRequest(number:')) {
      repository['r' + index] = { __typename: 'Issue', title: 'Local issue ' + number };
    } else if (query.includes('issue(number:')) {
      repository['i' + index] = {
        closed: true, stateReason: 'COMPLETED',
        labels: { nodes: [] }, timelineItems: { nodes: [] },
      };
    } else {
      throw new Error('Unexpected GraphQL query: ' + query);
    }
  }
  return new Response(JSON.stringify({ data: { repository } }), { status: 200 });
};
`);
  const output = join(directory, 'output');
  const result = spawnSync(process.execPath, [
    '--import', require.resolve('tsx'), '--require', preload, resolve(__dirname, '../src/generate.ts'),
  ], {
    cwd: directory,
    env: {
      ...env,
      GITHUB_REPOSITORY: 'camunda/camunda',
      GITHUB_OUTPUT: output,
      GITHUB_STEP_SUMMARY: join(directory, 'summary'),
      INPUT_TOKEN: 'test-token',
      'INPUT_TARGET-VERSION': '8.9.19',
      'INPUT_RELEASE-BRANCH': 'main',
      'INPUT_GATE-REQUIRED-AT': '',
      'INPUT_ALLOW-UNATTRIBUTED': String(allowUnattributed),
      'INPUT_UNATTRIBUTED-REASON': allowUnattributed ? 'approved test override' : '',
      'INPUT_OUTPUT-DIR': directory,
    },
    encoding: 'utf8',
    timeout: 30_000,
  });
  assert.ifError(result.error);
  assert.equal(result.status, allowUnattributed ? 0 : 1, result.stdout + result.stderr);
  const customerOutput = readFileSync(output, 'utf8').split('\n');
  return {
    customerBody: customerOutput.slice(1, -2).join('\n'),
    fullAsset: readFileSync(join(directory, 'CHANGELOG-8.9.19.md'), 'utf8'),
    changelog: JSON.parse(readFileSync(join(directory, 'changelog.json'), 'utf8')) as { prs: RenderPrInput[] },
    labels: JSON.parse(readFileSync(join(directory, 'labels.json'), 'utf8')) as { issues: number[]; pullRequests: number[] },
    comments: JSON.parse(readFileSync(join(directory, 'comments.json'), 'utf8')) as { entries: { issueNumber: number }[] },
    audit: JSON.parse(readFileSync(join(directory, 'audit.json'), 'utf8')) as { overrides: { number: number }[] },
    calls: JSON.parse(readFileSync(calls, 'utf8')) as { query: string; variables: Record<string, unknown> }[],
  };
}

for (const allowUnattributed of [false, true]) {
  test(`should not attribute a private native reference to a same-numbered local issue (override: ${allowUnattributed})`, (t) => {
    // given / when
    const result = generate(t, [{
      number: 1,
      title: 'fix: correct retries',
      closingIssues: [{ number: 4777, repository: { nameWithOwner: 'camunda/private-tracker' } }],
    }], allowUnattributed);

    // then
    assert.deepEqual(result.changelog.prs[0]!.issueNumbers, []);
    assert.deepEqual(result.changelog.prs[0]!.closesIssueNumbers, []);
    assert.equal(result.changelog.prs[0]!.attributionSource, 'unattributed');
    assert.deepEqual(result.labels.issues, []);
    assert.deepEqual(result.comments.entries, []);
    assert.deepEqual(result.audit.overrides.map((entry) => entry.number), allowUnattributed ? [1] : []);
    assert.equal(result.customerBody, '');
    assert.match(result.fullAsset, /fix: correct retries \(#1\)/);
    assert.doesNotMatch(result.fullAsset, /4777|private-tracker|Local issue/);
    assert.equal(result.calls.some((call) => Object.values(call.variables).includes(4777)), false);
  });
}

test('should keep same-repository native references alongside foreign ones', (t) => {
  // given / when
  const result = generate(t, [{
    number: 1,
    title: 'fix: correct retries',
    closingIssues: [
      { number: 4777, repository: { nameWithOwner: 'camunda/private-tracker' } },
      { number: 100, repository: { nameWithOwner: 'camunda/camunda' } },
    ],
  }]);

  // then
  assert.deepEqual(result.changelog.prs[0]!.issueNumbers, [100]);
  assert.deepEqual(result.changelog.prs[0]!.closesIssueNumbers, [100]);
  assert.equal(result.changelog.prs[0]!.attributionSource, 'closingIssuesReferences');
  assert.deepEqual(result.labels.issues, [100]);
  assert.deepEqual(result.comments.entries.map((entry) => entry.issueNumber), [100]);
  assert.match(result.customerBody, /Local issue 100 \(#100\)/);
  assert.doesNotMatch(result.fullAsset, /4777|private-tracker/);
});

test('should retain a dependency PR breaking-change label in both Markdown outputs', (t) => {
  // given / when
  const result = generate(t, [{
    number: 1,
    title: 'deps: bump pkg',
    body: '| Package | Change |\n|---|---|\n| [pkg](https://example.com/pkg) | `1.0` → `2.0` |',
    bot: true,
    breaking: true,
  }]);

  // then
  assert.equal(result.changelog.prs[0]!.breaking, true);
  for (const body of [result.customerBody, result.fullAsset]) {
    assert.match(body, /^## Breaking changes\n\n- pkg: 1\.0 → 2\.0 \(#1\)/);
    assert.match(body, /## Dependency updates/);
  }
});

for (const newerIsBot of [false, true]) {
  test(`should preserve walk order across dependency attribution buckets (newer bot: ${newerIsBot})`, (t) => {
    // given / when — PR numbers and mergedAt deliberately do not encode walk order
    const result = generate(t, [
      { number: 20, title: 'deps: bump pkg', body: '| Package | Change |\n|---|---|\n| [pkg](https://example.com/pkg) | `1.0` → `2.0` |', bot: !newerIsBot },
      { number: 10, title: 'deps: bump pkg', body: '| Package | Change |\n|---|---|\n| [pkg](https://example.com/pkg) | `2.0` → `3.0` |', bot: newerIsBot },
    ]);

    // then
    assert.match(result.fullAsset, /- pkg: 1\.0 → 3\.0 \(#10, #20\)$/m);
    assert.deepEqual(result.changelog.prs.map((pr) => pr.number), [10, 20]);
    assert.deepEqual(result.labels.pullRequests, [10, 20]);
    assert.deepEqual(result.audit.overrides.map((entry) => entry.number), [newerIsBot ? 20 : 10]);
    assert.match(result.customerBody, newerIsBot ? /pkg: 2\.0 → 3\.0 \(#10\)/ : /pkg: 1\.0 → 2\.0 \(#20\)/);
  });
}
