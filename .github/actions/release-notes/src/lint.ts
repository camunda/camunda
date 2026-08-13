import { GithubCommentApi, syncStickyComment } from './comment';
import { evaluateGate } from './gate';
import * as core from './gha';
import { GithubLabelApi, syncNoIssueLabel } from './labels';
import { GithubResolver } from './resolver';

/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on `pull_request`, so the workflow and this action resolve from
 * the PR head — the same trust model as every other lint in ci.yml, and the
 * reason there is no privileged token anywhere here. A fork PR gets a read-only
 * GITHUB_TOKEN and no secrets, which is exactly why the writes below are guarded
 * by `can-write` instead of failing.
 *
 * The PR number comes from the event payload; the body and title are then
 * fetched from the API, so they are current at evaluation time rather than a
 * snapshot from whenever the event fired (a PR edited twice in quick succession
 * must not be judged on the older body).
 *
 * ponytail: warn-only for now — reports the combined gate outcome (PR-issue link
 * + title lint, with a backport hop) to the job summary, the outputs, a single
 * sticky PR comment, and the display-only `no-issue` label. The check itself is
 * the job's own conclusion; GitHub renders it on the PR without us publishing
 * anything. Both syncs run regardless of `enforce` — they are informational, not
 * the enforcement mechanism. `enforce=true` flips a fail into a non-zero exit;
 * enforce mode ships in a follow-up PR.
 */
async function run(): Promise<void> {
  const token = core.getInput('token', { required: true });
  const enforce = core.getBooleanInput('enforce');
  // False on fork PRs: GitHub issues a read-only token and withholds secrets
  // there, whatever the workflow's `permissions:` block asks for. Everything the
  // gate READS still works, so it evaluates and reports normally — only the two
  // writes are skipped, and the log says so rather than surfacing a 403.
  const canWrite = core.getBooleanInput('can-write');

  const prNumberInput = core.getInput('pr-number').trim();
  const prNumber = Number(prNumberInput);
  if (!Number.isInteger(prNumber) || prNumber <= 0) {
    core.setFailed(`pr-number must be a positive integer, got "${prNumberInput}".`);
    return;
  }

  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
  const resolver = new GithubResolver(token, owner ?? '', repo ?? '');

  // A transient API error (403/500) must respect `enforce`: warn-only means the
  // gate never hard-fails, so a blip cannot turn a green check red.
  let gate;
  try {
    const pull = await resolver.fetchPull(prNumber);
    if (!pull) {
      core.info(`PR #${prNumber} could not be fetched; nothing to lint.`);
      return;
    }
    gate = await evaluateGate(resolver, {
      body: pull.body,
      title: pull.title,
      authorLogin: pull.authorLogin,
    });
  } catch (err) {
    const msg = `Release-notes gate could not be evaluated: ${err instanceof Error ? err.message : String(err)}`;
    if (enforce) core.setFailed(msg);
    else core.warning(`[warn-only] ${msg}`);
    return;
  }

  const failed = gate.checks.filter((check) => check.outcome === 'fail');
  const reasons = failed.flatMap((check) => check.reasons.map((reason) => `${check.label}: ${reason}`));

  core.setOutput('outcome', gate.outcome);
  core.setOutput('delivery-path', gate.deliveryPath);
  core.setOutput('failed-checks', failed.map((check) => check.label).join(','));

  // The job summary is the gate's primary report: it is the one channel that
  // works everywhere, fork PRs included, and needs no token at all.
  const heading = gate.outcome === 'pass' ? '✅ Release-notes checks passed' : '❌ Release-notes checks failed';
  const summaryLines = gate.checks.map(
    (check) => `${check.outcome === 'pass' ? '✅' : '❌'} ${check.label}: ${check.reasons.join(' ')}`,
  );
  await core.summary.addHeading(heading, 3).addList(summaryLines).write();

  if (!canWrite) {
    // Not a failure: a fork PR is still fully evaluated above, and the verdict is
    // in the summary and this log. Stated explicitly so the absence of the usual
    // comment reads as designed rather than broken.
    core.info('Fork pull request: no write token available, so the sticky comment and no-issue label are skipped.');
  } else {
    // The sticky comment and the display-only `no-issue` label. Independent of
    // each other, so run them concurrently. Each is best-effort: a sync failure
    // is logged and must never fail the gate — warn or not, the outcome above
    // stands.
    await Promise.allSettled([
      (async () => {
        try {
          const comments = new GithubCommentApi(token, owner ?? '', repo ?? '', prNumber);
          const action = await syncStickyComment(comments, gate);
          core.info(`Sticky comment: ${action}.`);
        } catch (err) {
          core.warning(`Sticky comment sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
        }
      })(),
      (async () => {
        try {
          const labels = new GithubLabelApi(token, owner ?? '', repo ?? '', prNumber);
          const action = await syncNoIssueLabel(labels, gate);
          core.setOutput('label-action', action);
          core.info(`no-issue label: ${action}.`);
        } catch (err) {
          core.warning(`Label sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
        }
      })(),
    ]);
  }

  if (gate.outcome === 'fail') {
    const msg = reasons.join(' ');
    if (enforce) core.setFailed(msg);
    else core.warning(`[warn-only] ${msg}`);
  } else {
    core.info('All release-notes checks passed.');
  }
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
