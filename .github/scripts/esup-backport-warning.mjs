// Warns when a change heads to an ESUP (extended support) branch. Warn only:
// nothing is skipped or blocked, because ESUP branches still need CVE and
// CI/release fixes backported.
//
// A branch `stable/X` is ESUP when the label `backport stable/X` has "ESUP" in
// its description. That label is updated by hand on the minor's
// end-of-standard-support date and becomes "OUTDATED, DON'T USE …" at EOL, so
// the warning follows the label without a list kept here.
//
// Modes (env MODE):
//   label            a `backport …` label was added      → plain comment
//   pull-request     a PR targets a branch (opened/edited) → plain comment
//   backport-summary backport-action ran                 → mark the ESUP rows
//                    of this run's summary comment and add a warning block;
//                    falls back to a plain comment if that comment isn't found
//
// Uses only Node built-ins and the preinstalled `gh` CLI (authenticated via
// GH_TOKEN), mirroring .github/scripts/align-renovate-assignees.mjs.

import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {pathToFileURL} from 'node:url';

const exec = promisify(execFile);

const BACKPORT_LABEL = /^backport ([^ ]+)$/;
const ESUP_MARKER = 'ESUP';
const ROW_MARKER = '⚠️ ESUP';
// Makes the summary edit idempotent when the job is re-run with the same run ID.
export const HIDDEN_MARKER = '<!-- esup-backport-warning -->';

const POLICY =
  'fixes for exploitable High/Critical vulnerabilities, CI or release-process fixes, ' +
  'or fixes a customer asked for through support';

/** Branches whose `backport <branch>` label marks them as ESUP, sorted and unique. */
export function esupBranchesFromLabels(labels) {
  const branches = new Set();
  for (const label of labels ?? []) {
    const match = BACKPORT_LABEL.exec(label?.name ?? '');
    if (match && (label.description ?? '').includes(ESUP_MARKER)) {
      branches.add(match[1]);
    }
  }
  return [...branches].sort();
}

/** "`a`", "`a` and `b`", "`a`, `b` and `c`". */
export function formatBranches(branches) {
  const quoted = branches.map((branch) => `\`${branch}\``);
  if (quoted.length <= 1) return quoted.join('');
  return `${quoted.slice(0, -1).join(', ')} and ${quoted.at(-1)}`;
}

/** The warning text for one of the three points (see the header). */
export function warningMessage(mode, branches) {
  const list = formatBranches(branches);
  const verb = branches.length > 1 ? 'are' : 'is';
  const intro = `⚠️ ${list} ${verb} in Extended Support (ESUP).`;
  switch (mode) {
    case 'label':
      return `${intro} Backport only ${POLICY}. If this change is none of those, remove the \`backport ${branches[0]}\` label.`;
    case 'pull-request':
      return `${intro} Only ${POLICY} belong on this branch. If this change is none of those, reconsider whether it belongs on this branch.`;
    case 'backport-summary': {
      const ending = branches.length > 1 ? 'close their backport PRs' : 'close its backport PR';
      return `${intro} Backport only ${POLICY}. If this change is none of those, ${ending}.`;
    }
    default:
      throw new Error(`Unknown mode: ${mode}`);
  }
}

/** The newest bot comment that links this workflow run (backport-action's summary). */
export function findSummaryComment(comments, runId) {
  const runLink = `[workflow run ${runId}](`;
  const matches = (comments ?? []).filter(
    (comment) => comment?.user?.type === 'Bot' && (comment.body ?? '').includes(runLink),
  );
  return matches.sort((a, b) => b.id - a.id)[0];
}

const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * Marks each ESUP row of backport-action's summary table and appends the
 * warning block. Returns the body unchanged when it was already annotated, or
 * null when a row is missing (unknown format: the caller falls back).
 */
export function annotateSummary(body, branches) {
  if (body.includes(HIDDEN_MARKER)) return body;
  let annotated = body;
  for (const branch of branches) {
    const row = new RegExp(`^(\\| \`${escapeRegExp(branch)}\` \\| .*?) \\|$`, 'm');
    if (!row.test(annotated)) return null;
    annotated = annotated.replace(row, `$1 · ${ROW_MARKER} |`);
  }
  const message = warningMessage('backport-summary', branches);
  return `${annotated}\n\n> [!WARNING]\n> ${message}\n${HIDDEN_MARKER}`;
}

async function gh(args) {
  const {stdout} = await exec('gh', args, {maxBuffer: 64 * 1024 * 1024});
  return stdout;
}

/** Every item of a paginated list endpoint (one JSON object per line; works on any gh version). */
async function ghList(path) {
  const out = await gh(['api', '--paginate', '--jq', '.[]', path]);
  return out.split('\n').filter((line) => line.trim()).map((line) => JSON.parse(line));
}

async function postComment(repo, prNumber, body) {
  await gh(['api', '-X', 'POST', `repos/${repo}/issues/${prNumber}/comments`, '-f', `body=${body}`]);
}

/** The `backport <branch>` label, or null when it doesn't exist. */
async function getBackportLabel(repo, branch) {
  const name = encodeURIComponent(`backport ${branch}`);
  try {
    return JSON.parse(await gh(['api', `repos/${repo}/labels/${name}`]));
  } catch (error) {
    if (`${error.stderr ?? ''}`.includes('HTTP 404')) return null;
    throw error;
  }
}

function requireEnv(name) {
  const value = (process.env[name] ?? '').trim();
  if (!value) throw new Error(`Missing required env ${name}`);
  return value;
}

async function main() {
  const mode = requireEnv('MODE');
  const repo = requireEnv('GITHUB_REPOSITORY');
  const prNumber = requireEnv('PR_NUMBER');

  if (mode === 'label') {
    const label = {name: requireEnv('LABEL_NAME'), description: process.env.LABEL_DESCRIPTION};
    const branches = esupBranchesFromLabels([label]);
    if (branches.length === 0) return console.log(`Not an ESUP backport label: ${label.name}`);
    await postComment(repo, prNumber, warningMessage('label', branches));
    return console.log(`Warned on #${prNumber} about ${branches.join(', ')}`);
  }

  if (mode === 'pull-request') {
    const base = requireEnv('BASE_REF');
    const label = await getBackportLabel(repo, base);
    if (!label || !esupBranchesFromLabels([label]).includes(base)) {
      return console.log(`Base ${base} is not an ESUP branch`);
    }
    await postComment(repo, prNumber, warningMessage('pull-request', [base]));
    return console.log(`Warned on #${prNumber} about ${base}`);
  }

  if (mode === 'backport-summary') {
    // The PR's current labels, as backport-action reads them: a re-run after a label was added
    // would otherwise miss that target.
    const labels = await ghList(`repos/${repo}/issues/${prNumber}/labels?per_page=100`);
    const branches = esupBranchesFromLabels(labels);
    if (branches.length === 0) return console.log('No ESUP backport targets');
    const runId = requireEnv('RUN_ID');
    const comments = await ghList(`repos/${repo}/issues/${prNumber}/comments?per_page=100`);
    const summary = findSummaryComment(comments, runId);
    const annotated = summary ? annotateSummary(summary.body, branches) : null;
    if (summary && annotated === summary.body) return console.log('Summary comment already has the ESUP warning');
    if (annotated) {
      await gh(['api', '-X', 'PATCH', `repos/${repo}/issues/comments/${summary.id}`, '-f', `body=${annotated}`]);
      return console.log(`Marked ${branches.join(', ')} in summary comment ${summary.id}`);
    }
    console.log('::warning::Backport summary comment not found or in an unknown format; posting a plain comment');
    await postComment(repo, prNumber, warningMessage('backport-summary', branches));
    return;
  }

  throw new Error(`Unknown MODE: ${mode}`);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  main().catch((error) => {
    console.error(`::error::${error.message}`);
    process.exit(1);
  });
}
