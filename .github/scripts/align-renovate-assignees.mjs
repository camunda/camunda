// Align assignees across Renovate PRs that update the same dependency to the
// same version on different base branches (e.g. main / stable/8.9 / stable/8.10).
//
// Why: Renovate opens one independent PR per base branch, and assignee selection
// (a random sample from a pool, configured in Mend) runs per-PR. The same bump
// therefore lands on three different people, so three engineers each review a
// slice of the same change. This reconciler collapses each such group onto a
// single assignee — deterministically the assignee of the lowest-numbered PR in
// the group — so one person owns the whole dependency across branches.
//
// It runs periodically (see renovate-align-assignees.yml) rather than on PR
// events: a scheduled pass is race-free (it reconciles whatever state exists)
// and only needs to live on the default branch, from where it sees open PRs on
// every base branch. Event-driven convergence would require the workflow on
// every stable branch and would have to handle the open-before-assigned race.
//
// Uses only Node built-ins and the preinstalled `gh` CLI (authenticated via
// GH_TOKEN), mirroring .github/actions/post-failure-feedback/feedback.mjs.

import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {appendFile} from 'node:fs/promises';

const exec = promisify(execFile);

const DRY_RUN = (process.env.DRY_RUN ?? '').trim() === 'true';
const REPO = (process.env.GITHUB_REPOSITORY ?? '').trim();
// Human signal to leave a PR alone; never touch assignees on these.
const HANDS_OFF_LABEL = 'stop-updating';
const DEPENDENCIES_LABEL = 'dependencies';

/** True when a PR author is the Renovate app under any of its login spellings. */
export function isRenovateAuthor(author) {
  if (!author) return false;
  const login = (author.login ?? '').toLowerCase();
  return (
    login === 'app/renovate' ||
    login === 'renovate[bot]' ||
    (author.is_bot === true && login.includes('renovate'))
  );
}

/**
 * The grouping key for a PR: its title with the trailing ` (<baseRef>)` that
 * Renovate appends when multiple base branches are configured removed. Siblings
 * for the same dependency+version share this key. We strip only the exact base
 * ref — never a generic trailing parenthesis — so unrelated titles that happen
 * to end in parentheses are not merged together.
 */
export function normalizeTitle(title, baseRef) {
  const t = (title ?? '').trim();
  const suffix = ` (${baseRef})`;
  return (t.endsWith(suffix) ? t.slice(0, -suffix.length) : t).trim();
}

/**
 * True when a normalized title pins a concrete dependency version or digest,
 * e.g. `… to v4.2.19.Final`, `… to v6`, `… Docker digest to sha256:…`.
 *
 * This is the guard against reconciling Renovate *grouped* updates. A grouped
 * PR (e.g. `Update all non-major dependencies`, `Update definitelyTyped`) keeps
 * a fixed group name in its title while its actual contents differ per base
 * branch — two branches can share the title yet update different packages, so
 * their independently chosen assignees must not be merged. Group titles carry
 * no concrete version target, so requiring one excludes them. Coherent groups
 * that do pin a shared version (e.g. `Update react monorepo to v18.3.0`) still
 * qualify, because at that version the branches really are the same bump.
 */
export function hasConcreteVersionTarget(title) {
  return /\bto\s+`?(v?\d[\w.+-]*|sha256:[0-9a-f]+|[0-9a-f]{7,40})`?\s*$/i.test(
    (title ?? '').trim(),
  );
}

/** Group PRs by normalized title. Returns a Map<key, pr[]>. */
export function groupSiblings(prs) {
  const groups = new Map();
  for (const pr of prs) {
    const key = normalizeTitle(pr.title, pr.baseRefName);
    const bucket = groups.get(key);
    if (bucket) bucket.push(pr);
    else groups.set(key, [pr]);
  }
  return groups;
}

/**
 * The single login every sibling in a group should converge to: one assignee of
 * the lowest-numbered PR that currently has at least one assignee — the first of
 * that PR's logins in sorted order, so the pick is deterministic even when the
 * seed PR carries more than one assignee. Returns a one-element array, or [] when
 * no PR in the group is assigned yet (nothing to propagate — a later pass handles
 * it once Renovate/Mend has assigned the seed PR). A one-element array keeps the
 * return shape uniform for computeAssigneeDiff.
 */
export function computeSeedAssignees(groupPrs) {
  const assigned = groupPrs
    .filter((pr) => (pr.assignees ?? []).length > 0)
    .sort((a, b) => a.number - b.number);
  if (assigned.length === 0) return [];
  const logins = assigned[0].assignees.map((a) => a.login).sort();
  return [logins[0]];
}

/** The add/remove needed to turn `current` logins into `desired` logins. */
export function computeAssigneeDiff(current, desired) {
  const cur = new Set(current);
  const des = new Set(desired);
  return {
    toAdd: desired.filter((l) => !cur.has(l)),
    toRemove: current.filter((l) => !des.has(l)),
  };
}

function hasLabel(pr, name) {
  return (pr.labels ?? []).some((l) => l.name === name);
}

async function listRenovateDependencyPrs() {
  const {stdout} = await exec('gh', [
    'pr',
    'list',
    '--repo',
    REPO,
    '--state',
    'open',
    '--label',
    DEPENDENCIES_LABEL,
    '--limit',
    '500',
    '--json',
    'number,title,baseRefName,author,assignees,labels',
  ]);
  const prs = JSON.parse(stdout);
  return prs.filter((pr) => isRenovateAuthor(pr.author));
}

async function applyAssignees(pr, toAdd, toRemove) {
  const args = ['pr', 'edit', String(pr.number), '--repo', REPO];
  if (toAdd.length > 0) args.push('--add-assignee', toAdd.join(','));
  if (toRemove.length > 0) args.push('--remove-assignee', toRemove.join(','));
  await exec('gh', args);
}

async function summarize(lines) {
  const summaryFile = process.env.GITHUB_STEP_SUMMARY;
  const body = lines.join('\n');
  console.log(body);
  if (summaryFile) await appendFile(summaryFile, body + '\n');
}

async function main() {
  if (!REPO) throw new Error('GITHUB_REPOSITORY is not set');

  const prs = await listRenovateDependencyPrs();
  const groups = groupSiblings(prs);

  const summary = [`## Renovate assignee alignment${DRY_RUN ? ' (dry run)' : ''}`];
  let changed = 0;

  for (const [key, groupPrs] of groups) {
    if (groupPrs.length < 2) continue;

    // Skip Renovate grouped updates: a shared group title does not guarantee the
    // branches update the same packages, so their assignees must not be merged.
    if (!hasConcreteVersionTarget(key)) {
      summary.push(`- _${key}_: ${groupPrs.length} PRs, no concrete version — skipped (grouped update)`);
      continue;
    }

    const seed = computeSeedAssignees(groupPrs);
    if (seed.length === 0) {
      summary.push(`- _${key}_: ${groupPrs.length} PRs, none assigned yet — skipped`);
      continue;
    }

    for (const pr of groupPrs) {
      if (hasLabel(pr, HANDS_OFF_LABEL)) continue;
      const current = (pr.assignees ?? []).map((a) => a.login);
      const {toAdd, toRemove} = computeAssigneeDiff(current, seed);
      if (toAdd.length === 0 && toRemove.length === 0) continue;

      summary.push(
        `- #${pr.number} (${pr.baseRefName}) → ${seed.join(', ')}` +
          `${toAdd.length ? ` +[${toAdd.join(', ')}]` : ''}` +
          `${toRemove.length ? ` -[${toRemove.join(', ')}]` : ''}`,
      );
      changed += 1;
      if (!DRY_RUN) await applyAssignees(pr, toAdd, toRemove);
    }
  }

  if (changed === 0) summary.push('- No changes needed.');
  await summarize(summary);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
