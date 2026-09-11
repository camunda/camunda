/**
 * The pure part of the range resolver (#50968): which previous point to diff
 * against, and how to turn git's answer into a deduped PR list. The git calls
 * themselves live in ./walk.
 */

// Alphas are 1-based: an `-alpha0` would make the previous-alpha baseline
// `-alpha-1`, a ref that cannot exist, so it is rejected as unrecognized.
//
// Dotted alphas (`8.8.0-alpha4.1`) are deliberately NOT accepted, though the
// 8.8 line has a couple and zcl parses them. The release process no longer
// cuts them, so this is a closed shape, not an oversight — do not widen the
// regex on the strength of those tags alone.
const VERSION = /^(\d+)\.(\d+)\.(\d+)(?:-alpha([1-9]\d*))?$/;

// Release candidates are 1-based too, and appear at every level: `8.9.0-rc1`,
// `8.7.6-rc2`, `8.10.0-alpha1-rc3`. Only the suffix is matched here — what it
// is attached to still has to satisfy VERSION.
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
  // A candidate is resolved from the version it is a candidate *for*, so the
  // shape of that version is validated first and names the errors below, even
  // though `rcN` for N > 1 short-circuits to the previous candidate.
  const rc = RC_SUFFIX.exec(target);
  const baseVersion = target.replace(RC_SUFFIX, '');
  const v = parseVersion(baseVersion, target);

  // An alpha is a pre-release of a minor, so it only ever carries patch 0.
  // Without this, `X.Y.1-alpha1` falls through to the previous-alpha branch and
  // resolves to `X.Y.1` — the target's own base version, a tag never cut.
  if (v.alpha !== null && v.patch !== 0) {
    throw new Error(
      `Unsupported release version "${target}": an alpha is a pre-release of a minor, so it must carry patch 0.`,
    );
  }

  // Candidates chain: `rcN` is diffed against `rc(N-1)`, matching how the
  // release actually publishes them. Each candidate is cut as its own GitHub
  // release, and zcl labels issues per candidate tag (`version:8.9.0-rc2`), so
  // a candidate's notes are the delta since the previous one; the final
  // untagged version then resolves normally and carries the whole release.
  // Reporting the full contents under every candidate instead would republish
  // rc1's entire changelog under rc2, rc3 and rc4.
  //
  // `rc1` has no previous candidate, so it falls through to the version it
  // stands for — which is also what makes the chain terminate somewhere real.
  if (rc && Number(rc[1]) > 1) {
    return { kind: 'previousTag', ref: `${baseVersion}-rc${Number(rc[1]) - 1}` };
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
 *  protected branch is a ruleset-bypass anomaly.
 *
 *  The `Revert "..."` form is the orphaned-tag repair: when a release job has to
 *  redo its own version bumps it reverts them first, so those reverts are as
 *  much release automation as the commits they undo. Without them here, every
 *  repaired release reports its reverts as ruleset bypasses. */
const AUTOMATION_WHITELIST = /^(?:Revert ")?\[maven-release-plugin\]/;

/** A release branch is `release-<version>` — `release-8.9.19`,
 *  `release-8.10.0-alpha5`. Anchored on the version so it cannot swallow a
 *  feature branch that merely starts with the word. */
const RELEASE_BRANCH = /^release-(\d+)\.(\d+)\.\d+/;

/**
 * The branches a delivered pull request in this release could have targeted.
 *
 * The release workflow's `RELEASE_BRANCH` is the TEMPORARY `release-X.Y.Z`
 * branch the tag is cut on, and nothing merges into that — delivered work
 * targets the line it was cut from. Passing the temporary branch straight into
 * the ambiguity rule below meant no candidate ever matched, so every commit
 * with more than one shipped pull request was skipped instead of resolved.
 *
 * Both line branches are accepted because either can be right: a patch and a
 * post-branch alpha ship from `stable/X.Y`, while an alpha cut before the
 * stable branch exists ships from `main`. Guessing between them would be
 * wrong half the time, and accepting both only ever narrows an ambiguity that
 * would otherwise be abandoned.
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
 * A release-branch merge-back delivers nothing of its own. It merges
 * `release-X.Y.Z` back into the line it was cut from, and everything it carries
 * was already published in that release's own notes. The branch shape is the
 * definition, not a heuristic: no other pull request goes from a release branch
 * into a stable line (or into `main`, for a pre-branch alpha).
 *
 * D25 asks for these to carry a `merge:` title at the source, which would also
 * exclude them via `categorize`. That fix lives in release automation outside
 * this repository and cannot reach pull requests that already merged, so the
 * topology is checked here as well. Relying on the title alone would put the
 * previous release's merge-back in every release's notes, and — because it
 * links no issue — in every release's unattributed bucket, failing the gate on
 * the same known-benign pull request every single time.
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
 * audit, never guess.
 *
 * `rangeShas` is the walk's own commits. A pull request ships in this range only
 * if its merge landed among them: commits pushed straight onto a release branch
 * — the release plugin's version bumps, and the reverts that repair an orphaned
 * tag — have no pull request of their own, so GitHub credits them to whichever
 * one later swept that branch into stable. That is the *next* release's
 * merge-back, whose merge commit is not in this range at all. Without the
 * check, 8.9.19's notes listed #62049, merged three days after the tag was cut.
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
      if (AUTOMATION_WHITELIST.test(commit.message)) continue;
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
