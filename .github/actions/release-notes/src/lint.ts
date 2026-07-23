import { readFileSync } from 'node:fs';
import { GithubCommentApi, syncStickyComment } from './comment';
import { evaluateGate } from './gate';
import * as core from './gha';
import { GithubLabelApi, syncNoIssueLabel } from './labels';
import { GithubResolver } from './resolver';

/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on pull_request_target, metadata-only. The PR body/title are
 * read from the event payload — never by checking out the PR head. The
 * privileged token comes in as an input (reused MONOREPO_RELEASE_APP).
 *
 * ponytail: warn-only for now — reports the combined gate outcome (PR-issue
 * link + title lint, with a backport hop) to the job summary, the outputs, a
 * single sticky PR comment (created only on failure, flipped to resolved once
 * fixed), and the display-only `no-issue` label. Both the comment and the
 * label sync regardless of `enforce` — they're informational, not the
 * enforcement mechanism. Enforce mode ships in a follow-up PR. `enforce=true`
 * flips a fail into a non-zero exit.
 */
async function run(): Promise<void> {
  const token = core.getInput('token', { required: true });
  const enforce = core.getBooleanInput('enforce');

  const eventPath = process.env.GITHUB_EVENT_PATH;
  const event = eventPath
    ? (JSON.parse(readFileSync(eventPath, 'utf8')) as {
        pull_request?: { body?: string; title?: string; number?: number; user?: { login?: string } };
      })
    : {};
  const pr = event.pull_request;
  if (!pr) {
    core.info('No pull_request in payload; nothing to lint.');
    return;
  }

  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
  const resolver = new GithubResolver(token, owner ?? '', repo ?? '');
  const gate = await evaluateGate(resolver, {
    body: pr.body ?? '',
    title: pr.title ?? '',
    authorLogin: pr.user?.login,
  });

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

  // Sticky PR comment (D24: comments from day one). A comment sync failure must
  // never fail the gate — warn or not, the outcome above stands.
  if (pr.number) {
    try {
      const comments = new GithubCommentApi(token, owner ?? '', repo ?? '', pr.number);
      const action = await syncStickyComment(comments, gate);
      core.info(`Sticky comment: ${action}.`);
    } catch (err) {
      core.warning(`Sticky comment sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
    }

    // Display-only `no-issue` label, mirroring the PR-issue-link check. Runs
    // during warn-only rollout too, so the label is already trustworthy by the
    // time enforce mode lands — a sync failure never fails the gate.
    try {
      const labels = new GithubLabelApi(token, owner ?? '', repo ?? '', pr.number);
      const action = await syncNoIssueLabel(labels, gate);
      core.setOutput('label-action', action);
      core.info(`no-issue label: ${action}.`);
    } catch (err) {
      core.warning(`Label sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
    }
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
