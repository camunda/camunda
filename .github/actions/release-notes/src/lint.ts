import { GithubCommentApi, syncStickyComment } from './comment';
import { evaluateGate } from './gate';
import * as core from './gha';
import { GithubLabelApi, syncNoIssueLabel } from './labels';
import { GithubResolver } from './resolver';

/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on `pull_request`, resolving from the PR head — no
 * privileged token anywhere here. A fork PR gets a read-only GITHUB_TOKEN
 * and no secrets, so the writes below are guarded by `can-write`, not left
 * to fail.
 *
 * Body/title are fetched fresh from the API rather than the event payload,
 * so a PR edited twice in quick succession is judged on the current body.
 *
 * ponytail: warn-only for now. `enforce=true` flips a fail into a non-zero
 * exit; enforce mode ships in a follow-up PR.
 */
async function run(): Promise<void> {
  const token = core.getInput('token', { required: true });
  const enforce = core.getBooleanInput('enforce');
  const canWrite = core.getBooleanInput('can-write'); // false on fork PRs — reads still work, only the two writes below are skipped

  const prNumberInput = core.getInput('pr-number').trim();
  const prNumber = Number(prNumberInput);
  if (!Number.isInteger(prNumber) || prNumber <= 0) {
    core.setFailed(`pr-number must be a positive integer, got "${prNumberInput}".`);
    return;
  }

  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
  const resolver = new GithubResolver(token, owner ?? '', repo ?? '');

  let gate; // a transient API error respects `enforce` too — warn-only means a blip can't turn a green check red
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

  // Job summary is the primary report — works everywhere, fork PRs included, no token needed.
  const heading = gate.outcome === 'pass' ? '✅ Release-notes checks passed' : '❌ Release-notes checks failed';
  const summaryLines = gate.checks.map(
    (check) => `${check.outcome === 'pass' ? '✅' : '❌'} ${check.label}: ${check.reasons.join(' ')}`,
  );
  await core.summary.addHeading(heading, 3).addList(summaryLines).write();

  if (!canWrite) {
    core.info('Fork pull request: no write token available, so the sticky comment and no-issue label are skipped.');
  } else {
    // Independent, so run concurrently. Each is best-effort — a sync failure
    // is logged and never fails the gate.
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
