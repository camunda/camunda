/**
 * The pure part of the range resolver (#50968): which previous point to diff
 * against, and how to turn git's answer into a deduped PR list. The git calls
 * themselves live in ./walk.
 */

// Alphas are 1-based: an `-alpha0` would make the previous-alpha baseline
// `-alpha-1`, a ref that cannot exist, so it is rejected as unrecognized.
const VERSION = /^(\d+)\.(\d+)\.(\d+)(?:-alpha([1-9]\d*))?$/;

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
  // Explicit null check, not truthiness: an `alpha: 0` reaching here would
  // otherwise format as a stable tag and send the walk at the wrong baseline.
  return v.alpha == null ? base : `${base}-alpha${v.alpha}`;
}

/** `minor - 1`, guarded: at minor 0 the previous line belongs to the previous
 *  major, whose last minor no arithmetic on this version string can name. */
function previousMinor(v: ParsedVersion, target: string): number {
  if (v.minor === 0) {
    throw new Error(
      `Unsupported release version "${target}": the baseline for the first minor of a major is the previous ` +
        `major's last minor, which cannot be derived from the version number alone.`,
    );
  }
  return v.minor - 1;
}

export type BaselineStrategy =
  | { readonly kind: 'previousTag'; readonly ref: string }
  | { readonly kind: 'forkPoint'; readonly otherRef: string };

/** The baseline to diff `target` against, from the version string alone — no
 *  tag list to consult, every case is arithmetic on the version number. */
export function resolveBaselineStrategy(target: string): BaselineStrategy {
  const v = parseVersion(target);

  // An alpha is a pre-release of a minor, so it only ever carries patch 0.
  // Without this, `X.Y.1-alpha1` falls through to the previous-alpha branch and
  // resolves to `X.Y.1` — the target's own base version, a tag never cut.
  if (v.alpha !== null && v.patch !== 0) {
    throw new Error(
      `Unsupported release version "${target}": an alpha is a pre-release of a minor, so it must carry patch 0.`,
    );
  }

  // alpha1-of-cycle: no prior tag on this line exists yet, so always the fork
  // point off the previous minor's stable branch, never a tag lookup (V5).
  if (v.alpha === 1 && v.patch === 0) {
    return { kind: 'forkPoint', otherRef: `origin/stable/${v.major}.${previousMinor(v, target)}` };
  }

  if (v.alpha !== null) {
    return { kind: 'previousTag', ref: format({ ...v, alpha: v.alpha - 1 }) };
  }

  if (v.patch > 0) {
    return { kind: 'previousTag', ref: format({ ...v, patch: v.patch - 1 }) };
  }

  // Minor release: fork point between the previous minor's release tag and this target.
  const previousMinorTag = format({ major: v.major, minor: previousMinor(v, target), patch: 0 });
  return { kind: 'forkPoint', otherRef: previousMinorTag };
}

/** The only legitimate PR-less commits (C12); anything else without a PR on a
 *  protected branch is a ruleset-bypass anomaly. */
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
 * Dedupe a first-parent commit walk to one entry per PR. Ambiguity rule: prefer
 * the PR targeting the release branch; still tied -> audit, never guess.
 */
export function resolveCommitsToPrs(commits: readonly WalkedCommit[], releaseBranch: string): RangeResolution {
  const reasons: string[] = [];
  // Insertion-ordered, so this both dedupes and preserves walk order.
  const prNumbers = new Set<number>();

  for (const commit of commits) {
    if (commit.associatedPrs.length === 0) {
      if (AUTOMATION_WHITELIST.test(commit.message)) continue;
      reasons.push(
        `Ruleset-bypass anomaly: commit ${commit.sha} has no associated pull request and does not match the automation whitelist.`,
      );
      continue;
    }

    if (commit.associatedPrs.length === 1) {
      prNumbers.add(commit.associatedPrs[0]!.number);
      continue;
    }

    const matchingBranch = commit.associatedPrs.filter((pr) => pr.baseRefName === releaseBranch);
    if (matchingBranch.length === 1) {
      prNumbers.add(matchingBranch[0]!.number);
    } else {
      const list = commit.associatedPrs.map((pr) => `#${pr.number}`).join(', ');
      reasons.push(
        `Ambiguous commit ${commit.sha}: associated with multiple pull requests (${list}) and no unique match targeting ${releaseBranch} — never guessing.`,
      );
    }
  }

  return { prNumbers: [...prNumbers], reasons };
}
