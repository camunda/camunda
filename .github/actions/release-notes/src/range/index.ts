/**
 * The pure part of the range resolver (#50968): baseline STRATEGY selection
 * (which previous point to diff against — never the actual git call) and the
 * commit-to-PR dedupe/ambiguity rules. The actual `merge-base`/`git log` calls
 * are the I/O part (a later step) — this module only decides what to ask git,
 * and how to turn its answer into a deduped PR list.
 */

const VERSION = /^(\d+)\.(\d+)\.(\d+)(?:-alpha(\d+))?$/;

interface ParsedVersion {
  readonly major: number;
  readonly minor: number;
  readonly patch: number;
  readonly alpha: number | null;
}

function parseVersion(version: string): ParsedVersion {
  const match = VERSION.exec(version);
  if (!match) throw new Error(`Not a recognized release version: "${version}"`);
  return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]), alpha: match[4] ? Number(match[4]) : null };
}

function format(v: Pick<ParsedVersion, 'major' | 'minor' | 'patch'> & { alpha?: number | null }): string {
  const base = `${v.major}.${v.minor}.${v.patch}`;
  return v.alpha ? `${base}-alpha${v.alpha}` : base;
}

export type BaselineStrategy =
  | { readonly kind: 'previousTag'; readonly ref: string }
  | { readonly kind: 'forkPoint'; readonly otherRef: string };

/**
 * Decide the baseline to diff `target` against, from the version string
 * alone — V5's alpha1-of-cycle rule is deliberately "always the fork point,
 * never a tag lookup", so this needs no tag list to consult; every other
 * case is pure arithmetic on the version number too (previous patch/alpha,
 * or the previous minor's own release tag by construction).
 */
export function resolveBaselineStrategy(target: string): BaselineStrategy {
  const v = parseVersion(target);

  // alpha1-of-cycle: the first alpha of a brand-new minor, no prior tag on
  // this line exists yet — ALWAYS the fork point off the previous minor's
  // stable branch, never a tag lookup (V5).
  if (v.alpha === 1 && v.patch === 0) {
    return { kind: 'forkPoint', otherRef: `origin/stable/${v.major}.${v.minor - 1}` };
  }

  if (v.alpha !== null) {
    return { kind: 'previousTag', ref: format({ ...v, alpha: v.alpha - 1 }) };
  }

  if (v.patch > 0) {
    return { kind: 'previousTag', ref: format({ ...v, patch: v.patch - 1 }) };
  }

  // Minor release (patch 0, not alpha): fork point between the previous
  // minor's own release tag and this target — kills the ancestry warning.
  const previousMinorTag = format({ major: v.major, minor: v.minor - 1, patch: 0 });
  return { kind: 'forkPoint', otherRef: previousMinorTag };
}

/** `[maven-release-plugin]` stub-segment commits are the only legitimate
 *  PR-less commits (C12) — everything else on a protected branch without a PR
 *  is a ruleset-bypass anomaly. */
const AUTOMATION_WHITELIST = /^\[maven-release-plugin\]/;

export interface WalkedCommit {
  readonly sha: string;
  readonly message: string;
  readonly associatedPrs: readonly { readonly number: number; readonly baseRefName: string }[];
}

export interface RangeResolution {
  readonly prNumbers: readonly number[];
  readonly reasons: readonly string[];
}

/**
 * Dedupe a first-parent commit walk down to one entry per PR, applying the
 * ambiguity rule (prefer the PR targeting the release branch; still tied ->
 * audit, never guess) and the PR-less-commit whitelist (C10/C12).
 */
export function resolveCommitsToPrs(commits: readonly WalkedCommit[], releaseBranch: string): RangeResolution {
  const prNumbers: number[] = [];
  const reasons: string[] = [];
  const seen = new Set<number>();

  const attribute = (pr: { readonly number: number }): void => {
    if (seen.has(pr.number)) return;
    seen.add(pr.number);
    prNumbers.push(pr.number);
  };

  for (const commit of commits) {
    if (commit.associatedPrs.length === 0) {
      if (AUTOMATION_WHITELIST.test(commit.message)) continue;
      reasons.push(
        `Ruleset-bypass anomaly: commit ${commit.sha} has no associated pull request and does not match the automation whitelist.`,
      );
      continue;
    }

    if (commit.associatedPrs.length === 1) {
      attribute(commit.associatedPrs[0]!);
      continue;
    }

    const matchingBranch = commit.associatedPrs.filter((pr) => pr.baseRefName === releaseBranch);
    if (matchingBranch.length === 1) {
      attribute(matchingBranch[0]!);
    } else {
      const list = commit.associatedPrs.map((pr) => `#${pr.number}`).join(', ');
      reasons.push(
        `Ambiguous commit ${commit.sha}: associated with multiple pull requests (${list}) and no unique match targeting ${releaseBranch} — never guessing.`,
      );
    }
  }

  return { prNumbers, reasons };
}
