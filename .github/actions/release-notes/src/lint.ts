import { readFileSync } from 'node:fs';
import { GithubCommentApi, syncStickyComment } from './comment';
import * as core from './gha';
import { extractSection, isOptOutTicked, parseRefs } from './parser';
import { decide } from './policy';
import { GithubResolver } from './resolver';

/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on pull_request_target, metadata-only. The PR body is read
 * from the event payload — never by checking out the PR head. The privileged
 * token comes in as an input (reused MONOREPO_RELEASE_APP).
 *
 * ponytail: warn-only for now — reports the decision to the job summary, the
 * outputs, and a single sticky PR comment (created only when the check fails,
 * flipped to resolved once fixed). Label sync and enforce mode ship in
 * follow-up PRs. `enforce=true` flips a fail into a non-zero exit.
 */
async function run(): Promise<void> {
  const token = core.getInput('token', { required: true });
  const enforce = core.getBooleanInput('enforce');

  const eventPath = process.env.GITHUB_EVENT_PATH;
  const event = eventPath
    ? (JSON.parse(readFileSync(eventPath, 'utf8')) as { pull_request?: { body?: string; number?: number } })
    : {};
  const pr = event.pull_request;
  if (!pr) {
    core.info('No pull_request in payload; nothing to lint.');
    return;
  }

  const body = pr.body ?? '';
  const section = extractSection(body);
  const optOut = isOptOutTicked(body);
  const refs = section ? parseRefs(section) : [];

  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
  const resolver = new GithubResolver(token, owner ?? '', repo ?? '');
  const resolved = await resolver.resolve(refs);
  const decision = decide(resolved, optOut);

  core.setOutput('outcome', decision.outcome);
  core.setOutput('code', decision.code);

  const heading = decision.outcome === 'pass' ? '✅ PR-issue link check passed' : '❌ PR-issue link check failed';
  await core.summary
    .addHeading(heading, 3)
    .addList(decision.reasons.slice())
    .write();

  // Sticky PR comment (D24: comments from day one). A comment sync failure must
  // never fail the gate — warn or not, the decision above stands.
  if (pr.number) {
    try {
      const comments = new GithubCommentApi(token, owner ?? '', repo ?? '', pr.number);
      const action = await syncStickyComment(comments, decision);
      core.info(`Sticky comment: ${action}.`);
    } catch (err) {
      core.warning(`Sticky comment sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  if (decision.outcome === 'fail') {
    const msg = decision.reasons.join(' ');
    if (enforce) core.setFailed(msg);
    else core.warning(`[warn-only] ${msg}`);
  } else {
    core.info(decision.reasons.join(' '));
  }
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
