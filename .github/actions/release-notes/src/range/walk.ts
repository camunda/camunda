import { execFileSync } from 'node:child_process';
import type { BaselineStrategy } from './index';

/**
 * The only place that shells out to git. Args go as an argv array
 * (execFileSync, no shell) so a ref can never be read as a shell command.
 */

export interface WalkedGitCommit {
  readonly sha: string;
  readonly message: string;
}

// Unit separator: never appears in a commit subject, unlike ":" or "|".
const FIELD_SEP = '\x1f';

/** Turn a `BaselineStrategy` into a ref: previousTag already names one, forkPoint needs `merge-base`. */
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
