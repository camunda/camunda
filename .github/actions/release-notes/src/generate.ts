import { writeFileSync } from 'node:fs';
import * as core from './gha';
import { processPr } from './pipeline';
import type { PipelineResolver } from './pipeline';
import { resolveBaselineStrategy, resolveCommitsToPrs } from './range';
import { resolveBaselineRef, walkFirstParent } from './range/walk';
import type { RenderPrInput } from './render';
import { render } from './render';
import { GithubGraphqlResolver } from './resolve';
import { GithubResolver } from './resolver';

/**
 * The `generate` entrypoint (release time). Wires the steps in order: range
 * walk (git) -> commit->PR mapping (GraphQL) -> PR metadata (GraphQL) -> per-PR
 * attribution + categorize (pipeline, REST for ref resolution and the backport
 * hop) -> render.
 *
 * Read-only by design; writing labels/comments is the cutover work unit (#57714).
 *
 * ponytail: `closesIssueNumbers` uses the PR's native `closingIssuesReferences`
 * as the "actually closed that issue" signal instead of a per-issue closer
 * lookup. A PR that really closed an issue but has an empty field (e.g. the
 * keyword was edited out) is under-reported as "Partially delivered" rather
 * than "Released"; closing that needs Issue.timelineItems, its own I/O step.
 * Exception: a backport-hop delivery is trusted wholesale (see below) — the
 * backport bot never writes closing keywords, so the field is always empty
 * for it and the general signal would under-report every single one.
 */

interface RunInputs {
  readonly token: string;
  readonly owner: string;
  readonly repo: string;
  readonly targetVersion: string;
  readonly releaseBranch: string;
  readonly gateRequiredAt: string | null;
  readonly allowUnattributed: boolean;
  readonly unattributedReason?: string;
  readonly outputDir: string;
}

/** `owner/repo` from the runner's own environment. Empty halves would reach
 *  GraphQL and come back as an opaque schema error, so they fail here instead. */
function readRepository(): { owner: string; repo: string } {
  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '').split('/');
  if (!owner || !repo) {
    throw new Error(`GITHUB_REPOSITORY must be set to "owner/repo", got "${process.env.GITHUB_REPOSITORY ?? ''}".`);
  }
  return { owner, repo };
}

function readInputs(): RunInputs {
  const { owner, repo } = readRepository();
  const gateRequiredAt = core.getInput('gate-required-at').trim();
  if (gateRequiredAt.length > 0 && Number.isNaN(Date.parse(gateRequiredAt))) {
    throw new Error(`gate-required-at must be a parseable date, got "${gateRequiredAt}".`);
  }
  return {
    token: core.getInput('token', { required: true }),
    owner,
    repo,
    targetVersion: core.getInput('target-version', { required: true }),
    releaseBranch: core.getInput('release-branch', { required: true }),
    gateRequiredAt: gateRequiredAt.length > 0 ? gateRequiredAt : null,
    allowUnattributed: core.getBooleanInput('allow-unattributed'),
    unattributedReason: core.getInput('unattributed-reason').trim() || undefined,
    outputDir: core.getInput('output-dir') || '.',
  };
}

async function run(): Promise<void> {
  const input = readInputs();
  const graphql = new GithubGraphqlResolver(input.token, input.owner, input.repo);
  const restResolver = new GithubResolver(input.token, input.owner, input.repo);
  const pipelineResolver: PipelineResolver = {
    resolveRefs: (refs) => restResolver.resolve(refs),
    fetchOriginalPull: (number, repo) => restResolver.fetchOriginalPull(number, repo),
    fetchIssueTitle: (number) => restResolver.fetchIssueTitle(number),
  };

  const strategy = resolveBaselineStrategy(input.targetVersion);
  const baseline = resolveBaselineRef(process.cwd(), strategy, input.targetVersion);
  const walked = walkFirstParent(process.cwd(), baseline, input.targetVersion);
  core.info(`Range ${baseline}..${input.targetVersion}: ${walked.length} first-parent commits.`);

  const commitMappings = await graphql.mapCommitsToPrs(walked.map((commit) => commit.sha));
  const commitsForDedupe = walked.map((commit, i) => ({
    sha: commit.sha,
    message: commit.message,
    associatedPrs: commitMappings[i]?.associatedPrs ?? [],
  }));
  const { prNumbers, reasons: rangeReasons } = resolveCommitsToPrs(commitsForDedupe, input.releaseBranch);
  for (const reason of rangeReasons) core.warning(reason);

  const metadata = await graphql.fetchPrMetadata(prNumbers);

  const attributed: RenderPrInput[] = [];
  const unattributed: RenderPrInput[] = [];
  for (const pr of metadata) {
    for (const field of pr.truncatedFields ?? []) {
      core.warning(`PR #${pr.number}: ${field} exceeded the 20-entry query cap — some entries were not read.`);
    }
    const output = await processPr(
      pipelineResolver,
      {
        number: pr.number,
        title: pr.title,
        body: pr.body,
        authorLogin: pr.authorLogin,
        mergedAt: pr.mergedAt,
        labels: pr.labels,
        closingIssuesReferences: pr.closingIssuesReferences,
      },
      { gateRequiredAt: input.gateRequiredAt },
    );

    if (output.anomaly) core.warning(`PR #${output.number}: ${output.anomaly} (${output.attribution.source}).`);
    for (const reason of output.attribution.reasons) core.warning(`PR #${output.number}: ${reason}`);
    for (const reason of output.categorization.reasons) core.warning(`PR #${output.number}: ${reason}`);

    const renderPr: RenderPrInput = {
      number: output.number,
      title: output.title,
      section: output.categorization.section,
      visibility: output.categorization.visibility,
      component: output.categorization.component,
      breaking: output.categorization.breaking,
      issueNumbers: output.attribution.issueNumbers,
      // A backport hop delivers via THIS PR's merge, but the backport bot never
      // writes a closing keyword — closingIssuesReferences is always empty for
      // it, so the general signal below would under-report every single one.
      closesIssueNumbers:
        output.attribution.deliveryPath === 'backportHop'
          ? output.attribution.issueNumbers
          : output.attribution.issueNumbers.filter((n) => pr.closingIssuesReferences.includes(n)),
      attributionSource: output.attribution.source,
    };

    // A `merge`-type PR (section: null) is excluded from every render() output
    // regardless of attribution, so it must never trip the unattributed guard.
    const bucketed =
      output.categorization.section !== null &&
      (output.attribution.source === 'unattributed' || output.attribution.source === 'resolutionFailed');
    (bucketed ? unattributed : attributed).push(renderPr);
  }

  const result = render(attributed, unattributed, {
    version: input.targetVersion,
    allowUnattributed: input.allowUnattributed,
    unattributedReason: input.unattributedReason,
  });

  writeFileSync(`${input.outputDir}/CHANGELOG-${input.targetVersion}.md`, result.fullAsset);
  writeFileSync(`${input.outputDir}/changelog.json`, JSON.stringify(result.changelogJson, null, 2));
  writeFileSync(`${input.outputDir}/labels.json`, JSON.stringify(result.labelsJson, null, 2));
  writeFileSync(`${input.outputDir}/audit.json`, JSON.stringify(result.auditJson, null, 2));
  writeFileSync(`${input.outputDir}/comments.json`, JSON.stringify(result.commentsJson, null, 2));
  core.setOutput('customer-body', result.customerBody);

  // Both bodies, so a reviewer can see exactly what the customer gets vs. the
  // full internal asset — same rendering guard as every other output: written
  // even when the unattributed guard trips, never skipped on failure.
  await core.summary
    .addHeading(`Release notes — ${input.targetVersion}`, 2)
    .addHeading('Customer-facing body', 3)
    .addRaw(result.customerBody)
    .addHeading('Full asset (includes internal-only sections)', 3)
    .addRaw(result.fullAsset)
    .write();

  // Every output above is written even when the unattributed guard trips —
  // audit.json's whole purpose is explaining which PRs and why — so the job
  // fails only AFTER the diagnostic outputs exist on disk.
  if (result.failureReason) throw new Error(result.failureReason);

  core.info(`Generated release notes for ${input.targetVersion}: ${attributed.length} attributed PR(s).`);
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
