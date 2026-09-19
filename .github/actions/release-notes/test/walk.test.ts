import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, test } from 'node:test';
import { resolveBaselineRef, walkFirstParent } from '../src/range/walk';

const env = {
  ...process.env,
  GIT_AUTHOR_NAME: 'test',
  GIT_AUTHOR_EMAIL: 'test@example.com',
  GIT_COMMITTER_NAME: 'test',
  GIT_COMMITTER_EMAIL: 'test@example.com',
};

function git(repoDir: string, args: string[]): string {
  return execFileSync('git', args, { cwd: repoDir, env, encoding: 'utf8' });
}

function commit(repoDir: string, file: string, message: string): string {
  writeFileSync(join(repoDir, file), message);
  git(repoDir, ['add', file]);
  git(repoDir, ['commit', '-m', message]);
  return git(repoDir, ['rev-parse', 'HEAD']).trim();
}

/**
 * Builds: A (base) -> [feature branch: B1, B2] -> M (merge, main) -> C (main).
 * `git log base..target --first-parent` from C must return [C, M] only —
 * B1/B2 live on the merge's second parent and must be skipped.
 */
function buildFixtureRepo(): { repoDir: string; base: string; target: string; merge: string; head: string } {
  const repoDir = mkdtempSync(join(tmpdir(), 'release-notes-walk-'));
  git(repoDir, ['init', '-q', '-b', 'main', repoDir]);
  const base = commit(repoDir, 'a.txt', 'A');
  git(repoDir, ['tag', 'base']);

  git(repoDir, ['checkout', '-q', '-b', 'feature']);
  commit(repoDir, 'b1.txt', 'B1');
  commit(repoDir, 'b2.txt', 'B2');

  git(repoDir, ['checkout', '-q', 'main']);
  git(repoDir, ['merge', '--no-ff', '-q', '-m', 'M', 'feature']);
  const merge = git(repoDir, ['rev-parse', 'HEAD']).trim();

  const head = commit(repoDir, 'c.txt', 'C');
  git(repoDir, ['tag', 'target']);

  return { repoDir, base, target: 'target', merge, head };
}

const repos: string[] = [];
after(() => {
  for (const dir of repos) rmSync(dir, { recursive: true, force: true });
});

test('--first-parent walk skips the merge commit\'s second-parent chain', () => {
  const { repoDir, base, target, merge, head } = buildFixtureRepo();
  repos.push(repoDir);
  const commits = walkFirstParent(repoDir, base, target);
  assert.deepEqual(
    commits.map((c) => c.sha),
    [head, merge],
  );
  assert.deepEqual(
    commits.map((c) => c.message),
    ['C', 'M'],
  );
});

test('an empty range (baseline == target) returns no commits', () => {
  const { repoDir, target } = buildFixtureRepo();
  repos.push(repoDir);
  assert.deepEqual(walkFirstParent(repoDir, target, target), []);
});

test('a previousTag baseline strategy resolves straight to that tag, no git call needed', () => {
  const { repoDir } = buildFixtureRepo();
  repos.push(repoDir);
  assert.equal(resolveBaselineRef(repoDir, { kind: 'previousTag', ref: 'base' }, 'target'), 'base');
});

test('a forkPoint baseline strategy resolves via git merge-base', () => {
  const { repoDir, base } = buildFixtureRepo();
  repos.push(repoDir);
  assert.equal(resolveBaselineRef(repoDir, { kind: 'forkPoint', otherRef: 'base' }, 'target'), base);
});
