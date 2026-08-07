import { GithubCheckRunApi, syncCheckRun } from './checkrun';
import { GithubCommentApi, syncStickyComment } from './comment';
import { evaluateGate } from './gate';
import * as core from './gha';
import { GithubLabelApi, syncNoIssueLabel } from './labels';
import { GithubResolver } from './resolver';

/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on `workflow_run`, so the workflow and this action always
 * resolve from the default branch and a PR cannot edit the code that judges it.
 * The PR is identified by `pr-number`, which the workflow derives server-side
 * from the immutable head SHA — never from anything the PR controls. The body
 * and title are then fetched from the API, so they are current at evaluation
 * time rather than a snapshot from whenever the event fired.
 *
 * ponytail: warn-only for now — reports the combined gate outcome (PR-issue
 * link + title lint, with a backport hop) to the job summary, the outputs, a
 * check run on the head SHA, a single sticky PR comment, and the display-only
 * `no-issue` label. All three syncs run regardless of `enforce` — they are
 * informational, not the enforcement mechanism. `enforce=true` flips a fail
 * into a non-zero exit; enforce mode ships in a follow-up PR.
 */
async function run(): Promise<void> {
  const token = core.getInput('token', { required: true });
  const checksToken = core.getInput('checks-token', { required: true });
  const headSha = core.getInput('head-sha', { required: true });
  const enforce = core.getBooleanInput('enforce');

  // Empty means the head SHA resolved to no pull request — a workflow_run from
  // something that is not a PR. Nothing to lint, and not an error.
  const prNumberInput = core.getInput('pr-number').trim();
  if (prNumberInput === '') {
    core.info('No pull request for this head SHA; nothing to lint.');
    return;
  }
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

  const heading = gate.outcome === 'pass' ? '✅ Release-notes checks passed' : '❌ Release-notes checks failed';
  const summaryLines = gate.checks.map(
    (check) => `${check.outcome === 'pass' ? '✅' : '❌'} ${check.label}: ${check.reasons.join(' ')}`,
  );
  await core.summary.addHeading(heading, 3).addList(summaryLines).write();

  // The check run (the PR's only visible signal, since a workflow_run job does
  // not attach to the PR), the sticky comment, and the display-only `no-issue`
  // label. Independent of each other, so run them concurrently. Each is
  // best-effort: a sync failure is logged and must never fail the gate — warn
  // or not, the outcome above stands.
  await Promise.allSettled([
    (async () => {
      try {
        const checks = new GithubCheckRunApi(checksToken, owner ?? '', repo ?? '');
        const action = await syncCheckRun(checks, headSha, gate, enforce);
        core.setOutput('check-run-action', action);
        core.info(`Check run: ${action}.`);
      } catch (err) {
        core.warning(`Check run sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
      }
    })(),
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

  if (gate.outcome === 'fail') {
    const msg = reasons.join(' ');
    if (enforce) core.setFailed(msg);
    else core.warning(`[warn-only] ${msg}`);
  } else {
    core.info('All release-notes checks passed.');
  }
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
