import { execFileSync } from 'node:child_process';
import type { BaselineStrategy } from './index';

/**
 * The one place that actually runs `git log`. ponytail: a single function
 * that runs one command and parses its output — no generic shell-runner
 * abstraction for a package that shells out exactly once. Args are passed as
 * an argv array (execFileSync, no shell) so a baseline/target ref can never
 * be interpreted as a shell command.
 */

export interface WalkedGitCommit {
  readonly sha: string;
  readonly message: string;
}

// Unit separator: never appears in a commit subject, unlike ":" or "|".
const FIELD_SEP = '\x1f';

/**
 * Turn a `BaselineStrategy` (the pure decision from `resolveBaselineStrategy`)
 * into an actual commit SHA/ref. A previousTag strategy already names the ref
 * to diff against; a forkPoint strategy needs the one real git call this
 * package makes for that purpose (`merge-base`).
 */
export function resolveBaselineRef(repoDir: string, strategy: BaselineStrategy, target: string): string {
  if (strategy.kind === 'previousTag') return strategy.ref;
  return execFileSync('git', ['merge-base', target, strategy.otherRef], { cwd: repoDir, encoding: 'utf8' }).trim();
}

export function walkFirstParent(repoDir: string, baseline: string, target: string): WalkedGitCommit[] {
  const output = execFileSync(
    'git',
    ['log', `${baseline}..${target}`, '--first-parent', `--format=%H${FIELD_SEP}%s`],
    { cwd: repoDir, encoding: 'utf8' },
  );
  return output
    .split('\n')
    .filter((line) => line.length > 0)
    .map((line) => {
      const [sha, message] = line.split(FIELD_SEP);
      return { sha: sha!, message: message ?? '' };
    });
}
