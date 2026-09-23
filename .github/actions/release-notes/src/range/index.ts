/**
 * The pure part of the range resolver: which previous point to diff against,
 * and how to turn git's answer into a deduped PR list. The git calls
 * themselves live in ./walk. See GENERATOR.md § 1 for the baseline table.
 */

// Alphas and candidates are both 1-based (no `-alpha0`/`-rc0`). Dotted alphas
// (`8.8.0-alpha4.1`) are deliberately unaccepted — a closed shape from a
// retired process, not an oversight.
const VERSION = /^(\d+)\.(\d+)\.(\d+)(?:-alpha([1-9]\d*))?$/;
const RC_SUFFIX = /-rc([1-9]\d*)$/;

interface ParsedVersion {
  readonly major: number;
  readonly minor: number;
  readonly patch: number;
  readonly alpha: number | null;
}

function parseVersion(version: string, reportAs: string = version): ParsedVersion {
  const match = VERSION.exec(version);
  if (!match) throw new Error(`Not a recognized release version: "${reportAs}"`);
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
  // Validated against the version a candidate is FOR, so `rcN > 1`'s
  // short-circuit below still gets the right error on a bad shape.
  const rc = RC_SUFFIX.exec(target);
  const baseVersion = target.replace(RC_SUFFIX, '');
  const v = parseVersion(baseVersion, target);

  if (v.alpha !== null && v.patch !== 0) {
    throw new Error(
      `Unsupported release version "${target}": an alpha is a pre-release of a minor, so it must carry patch 0.`,
    );
  }

  // rcN -> rc(N-1); rc1 has no previous candidate and falls through below.
  if (rc && Number(rc[1]) > 1) {
    return { kind: 'previousTag', ref: `${baseVersion}-rc${Number(rc[1]) - 1}` };
  }

  // alpha1-of-cycle: no prior tag on this line yet, so fork off stable/<prev minor>.
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

/** The only legitimate PR-less commits — release-plugin version bumps, and
 *  the reverts that repair an orphaned tag. See GENERATOR.md § 1. */
const AUTOMATION_WHITELIST = /^(?:Revert ")?\[maven-release-plugin\]/;

/** A release branch is `release-<version>` — `release-8.9.19`,
 *  `release-8.10.0-alpha5`. Anchored on the version so it cannot swallow a
 *  feature branch that merely starts with the word. */
const RELEASE_BRANCH = /^release-(\d+)\.(\d+)\.\d+/;

/**
 * The branches a delivered pull request in this release could have targeted.
 * `RELEASE_BRANCH` is the temporary `release-X.Y.Z` tag branch, which nothing
 * merges into, so this maps it to the real line(s): `stable/X.Y` for a patch
 * or post-branch alpha, `main` for a pre-branch alpha. Both are accepted —
 * guessing between them would be wrong half the time.
 */
function releaseLineBranches(releaseBranch: string): string[] {
  const match = RELEASE_BRANCH.exec(releaseBranch);
  return match ? [`stable/${match[1]}.${match[2]}`, 'main'] : [releaseBranch];
}

export interface WalkedCommit {
  readonly sha: string;
  readonly message: string;
  readonly associatedPrs: readonly {
    readonly number: number;
    readonly baseRefName: string;
    readonly headRefName: string;
    readonly mergeCommitOid: string | null;
  }[];
}

export interface RangeResolution {
  readonly prNumbers: readonly number[];
  readonly reasons: readonly string[];
}

/**
 * A release-branch merge-back delivers nothing of its own — it merges
 * `release-X.Y.Z` back into the line it was cut from, already published in
 * that release's own notes. Checked by branch topology, not title, since
 * that also catches ones merged before this rule existed. See GENERATOR.md § 1.
 */
function isReleaseMergeBack(pr: WalkedCommit['associatedPrs'][number]): boolean {
  // Version-shaped, not a bare `release-` prefix: a feature branch called
  // `release-notes-gate` targeting a stable line is ordinary work, not a
  // merge-back, and excluding it would drop real delivered work.
  return RELEASE_BRANCH.test(pr.headRefName) && (pr.baseRefName.startsWith('stable/') || pr.baseRefName === 'main');
}

/**
 * Dedupe a first-parent commit walk to one entry per PR. Ambiguity rule: prefer
 * the PR targeting the release LINE (see `releaseLineBranches`); still tied ->
 * audit, never guess. `rangeShas` restricts "shipped" to merges actually inside
 * the walk — a commit's associated PR can otherwise be the *next* release's
 * merge-back, merged after this tag was cut. See GENERATOR.md § 1.
 */
export function resolveCommitsToPrs(
  commits: readonly WalkedCommit[],
  releaseBranch: string,
  rangeShas: ReadonlySet<string>,
): RangeResolution {
  const reasons: string[] = [];
  const lineBranches = releaseLineBranches(releaseBranch);
  // Insertion-ordered, so this both dedupes and preserves walk order.
  const prNumbers = new Set<number>();

  for (const commit of commits) {
    // Checked first, ahead of any associated-PR anomaly below: a release-plugin
    // commit must never be attributed to a pull request, even one GitHub
    // reports with a null mergeCommitOid (which the shipped-anomaly branch
    // below otherwise keeps unconditionally).
    if (AUTOMATION_WHITELIST.test(commit.message)) continue;

    // Three outcomes, kept apart because they are three different facts about a
    // commit and collapsing them produces a wrong audit line: a merge-back is
    // excluded even though it merged here, while an out-of-range PR is excluded
    // precisely because it did not.
    const mergeBacks = commit.associatedPrs.filter(isReleaseMergeBack);
    const candidates = commit.associatedPrs.filter((pr) => !isReleaseMergeBack(pr));
    const shipped = candidates.filter((pr) => {
      if (pr.mergeCommitOid === null) {
        // Keep it: a MERGED pull request without a merge commit is a GitHub
        // data anomaly, and under-inclusion is the failure this epic exists to
        // fix. Say so, so the operator can check rather than wonder.
        reasons.push(`PR #${pr.number}: GitHub reported no merge commit — kept, but its range membership is unverified.`);
        return true;
      }
      return rangeShas.has(pr.mergeCommitOid);
    });

    if (shipped.length === 0) {
      // Credited only to a merge-back: either the merge-back commit itself, or
      // a commit pushed straight onto the release branch that one swept in.
      // Release plumbing either way — nothing delivered, nothing to report.
      if (mergeBacks.length > 0 && candidates.length === 0) continue;
      const list = candidates.map((pr) => `#${pr.number}`).join(', ');
      reasons.push(
        candidates.length === 0
          ? `Ruleset-bypass anomaly: commit ${commit.sha} has no associated pull request and does not match the automation whitelist.`
          : `Commit ${commit.sha} is credited only to pull requests that did not merge inside this range (${list}), and its message does not match the automation whitelist — excluded.`,
      );
      continue;
    }

    if (shipped.length === 1) {
      prNumbers.add(shipped[0]!.number);
      continue;
    }

    const matchingBranch = shipped.filter((pr) => lineBranches.includes(pr.baseRefName));
    if (matchingBranch.length === 1) {
      prNumbers.add(matchingBranch[0]!.number);
    } else {
      const list = shipped.map((pr) => `#${pr.number}`).join(', ');
      reasons.push(
        `Ambiguous commit ${commit.sha}: associated with multiple pull requests (${list}) and no unique match targeting ${lineBranches.join(' or ')} — never guessing.`,
      );
    }
  }

  return { prNumbers: [...prNumbers], reasons };
}
