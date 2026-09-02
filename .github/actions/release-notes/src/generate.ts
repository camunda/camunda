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
 * The `generate` entrypoint (release time). Wires steps 1-6 together in
 * order: range walk (git) -> commit->PR resolve (GraphQL phase 1) -> PR
 * metadata (GraphQL phase 2) -> per-PR attribution+categorize (pipeline,
 * REST resolver for ref resolution + the backport hop) -> render.
 *
 * Read-only by design (no write permissions anywhere in this job) — writing
 * labels/comments is the separate cutover work unit (#57714, 3-job split).
 *
 * ponytail: closesIssueNumbers uses the PR's native `closingIssuesReferences`
 * field as the "this PR actually closed that issue" signal, rather than a
 * separate Issue-state fetch. Validated against a real backport-heavy range
 * (camunda/camunda 8.8.36..8.8.37): `closingIssuesReferences` is empty on
 * every bot backport by construction (confirmed live), which happens to
 * match reality there — the closing event is virtually always the original
 * PR merging to main, never the stable-branch backport, so "Partially
 * delivered" is correct for every backport-delivered issue observed. The
 * theoretical gap this proxy does NOT cover: a PR that is itself the real,
 * global closer of an issue but whose `closingIssuesReferences` was for some
 * other reason empty (e.g. the closing keyword lived in a since-edited body)
 * would be under-reported as "Partially delivered" rather than "Released".
 * Closing that gap needs a per-issue closer lookup (Issue.timelineItems),
 * which is its own I/O step, not something to add speculatively here.
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

function readInputs(): RunInputs {
  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
  const gateRequiredAt = core.getInput('gate-required-at').trim();
  return {
    token: core.getInput('token', { required: true }),
    owner: owner ?? '',
    repo: repo ?? '',
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
    fetchOriginalPull: (number) => restResolver.fetchPull(number),
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
      closesIssueNumbers: output.attribution.issueNumbers.filter((n) => pr.closingIssuesReferences.includes(n)),
      attributionSource: output.attribution.source,
    };

    const bucketed = output.attribution.source === 'unattributed' || output.attribution.source === 'resolutionFailed';
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

  core.info(`Generated release notes for ${input.targetVersion}: ${attributed.length} attributed PR(s).`);
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
