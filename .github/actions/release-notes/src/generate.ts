import { mkdirSync, writeFileSync } from 'node:fs';
import * as core from './gha';
import { processPr } from './pipeline';
import type { OriginalPull, PipelineResolver } from './pipeline';
import { resolveBaselineStrategy, resolveCommitsToPrs } from './range';
import { resolveBaselineRef, walkFirstParent } from './range/walk';
import type { RenderPrInput } from './render';
import { render, emptyCustomerBodyWarning } from './render';
import { hiddenFromCustomerBody } from './categorize';
import { closesIssueNumbers } from './delivery';
import type { DeliveryInput } from './delivery';
import { GithubGraphqlResolver } from './resolve';
import type { AssociatedPr, ClassifiedRef, PrMetadata } from './resolve';
import { backportTargets, buildPipelineResolver, prewarmNumbers } from './resolve/warm';
import { GithubResolver } from './resolver';

/**
 * The `generate` entrypoint (release time). Wires the steps in order: range
 * walk (git) -> commit->PR mapping (GraphQL) -> PR metadata (GraphQL) -> per-PR
 * attribution + categorize (pipeline) -> render. Read-only by design; writing
 * labels/comments is a separate cutover unit.
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
  const ownRepo = `${input.owner}/${input.repo}`;
  const warmRefs = new Map<number, ClassifiedRef>();
  const originals = new Map<number, OriginalPull>();
  const pipelineResolver = buildPipelineResolver(restResolver, warmRefs, ownRepo, originals);

  const strategy = resolveBaselineStrategy(input.targetVersion);
  const baseline = resolveBaselineRef(process.cwd(), strategy, input.targetVersion);
  const walked = walkFirstParent(process.cwd(), baseline, input.targetVersion);
  core.info(`Range ${baseline}..${input.targetVersion}: ${walked.length} first-parent commits.`);

  const rangeShas = new Set(walked.map((commit) => commit.sha));

  // GitHub writes the PR number into the merge-commit subject, so most commits'
  // mapping is already in hand without walking branch history — but a guessed
  // number is only confirmed against the PR's own `mergeCommit`; anything
  // unconfirmed falls back to the original `associatedPullRequests` query.
  const candidateBySha = new Map<string, number>();
  for (const commit of walked) {
    const match = /\(#(\d+)\)\s*$/.exec(commit.message);
    if (match) candidateBySha.set(commit.sha, Number(match[1]));
  }

  const metaByNumber = new Map<number, PrMetadata>();
  for (const meta of await graphql.fetchPrMetadata([...new Set(candidateBySha.values())], true)) {
    metaByNumber.set(meta.number, meta);
  }

  const confirmed = new Map<string, AssociatedPr>();
  const unconfirmed: string[] = [];
  for (const commit of walked) {
    const candidate = candidateBySha.get(commit.sha);
    const meta = candidate === undefined ? undefined : metaByNumber.get(candidate);
    if (meta && meta.mergeCommitOid === commit.sha) {
      confirmed.set(commit.sha, {
        number: meta.number,
        baseRefName: meta.baseRefName,
        headRefName: meta.headRefName,
        mergeCommitOid: meta.mergeCommitOid,
      });
    } else {
      unconfirmed.push(commit.sha);
    }
  }
  core.info(`Mapped ${confirmed.size} commits from their own subject; ${unconfirmed.length} need the commit-to-PR query.`);

  const fallbackBySha = new Map(
    (await graphql.mapCommitsToPrs(unconfirmed)).map((mapping) => [mapping.sha, mapping.associatedPrs]),
  );
  const commitsForDedupe = walked.map((commit) => {
    const one = confirmed.get(commit.sha);
    return {
      sha: commit.sha,
      message: commit.message,
      associatedPrs: one ? [one] : (fallbackBySha.get(commit.sha) ?? []),
    };
  });
  const { prNumbers, reasons: rangeReasons } = resolveCommitsToPrs(commitsForDedupe, input.releaseBranch, rangeShas);
  for (const reason of rangeReasons) core.warning(reason);

  for (const meta of await graphql.fetchPrMetadata(prNumbers.filter((number) => !metaByNumber.has(number)))) {
    metaByNumber.set(meta.number, meta);
  }
  const metadata = prNumbers.map((number) => metaByNumber.get(number)).filter((meta): meta is PrMetadata => meta !== undefined); // walk order, kept stable

  // One bulk query for every backport's original, instead of one REST call per
  // backport when the pipeline's hop follows the marker. The speculative batch
  // drops unmerged PRs, but a backport can point at a closed-unmerged original
  // that still carries attribution, so a miss is left to the REST fallback.
  const targets = backportTargets(metadata.map((pr) => pr.body), ownRepo);
  const originalByNumber = new Map((await graphql.fetchPrMetadata(targets, true)).map((meta) => [meta.number, meta]));
  for (const meta of originalByNumber.values()) {
    originals.set(meta.number, { body: meta.body, title: meta.title, authorLogin: meta.authorLogin, mergedAt: meta.mergedAt });
  }
  core.info(`Prefetched ${originalByNumber.size} of ${targets.length} backport originals in ${Math.ceil(targets.length / 100)} requests.`);

  // Pre-warm every ref the pipeline might resolve — the release's own bodies and
  // the originals the hop reads — plus the closing issues whose titles it shows.
  const wanted = prewarmNumbers([...metadata.map((pr) => pr.body), ...[...originalByNumber.values()].map((meta) => meta.body)], ownRepo);
  for (const pr of metadata) for (const number of pr.closingIssuesReferences) wanted.add(number);
  for (const [number, classified] of await graphql.classifyRefs([...wanted])) {
    warmRefs.set(number, classified);
  }
  core.info(`Pre-classified ${warmRefs.size} distinct references in ${Math.ceil(wanted.size / 100)} requests.`);

  /** Warnings are collected, not emitted, so the log stays in walk order no
   *  matter which worker finishes first. */
  interface Processed {
    /** `closesIssueNumbers` needs the issue-side lookup below, so it's added later. */
    readonly renderPr: Omit<RenderPrInput, 'closesIssueNumbers'>;
    readonly delivery: Omit<DeliveryInput, 'issueNumbers'>;
    readonly bucketed: boolean;
    readonly warnings: readonly string[];
  }

  const processOne = async (pr: PrMetadata): Promise<Processed> => {
    const warnings: string[] = (pr.truncatedFields ?? []).map(
      (field) => `PR #${pr.number}: ${field} exceeded the 20-entry query cap — some entries were not read.`,
    );

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

    if (output.anomaly) warnings.push(`PR #${output.number}: ${output.anomaly} (${output.attribution.source}).`);
    for (const reason of output.attribution.reasons) warnings.push(`PR #${output.number}: ${reason}`);
    for (const reason of output.categorization.reasons) warnings.push(`PR #${output.number}: ${reason}`);

    return {
      renderPr: {
        number: output.number,
        title: output.title,
        section: output.categorization.section,
        visibility: output.categorization.visibility,
        component: output.categorization.component,
        breaking: output.categorization.breaking,
        issueNumbers: output.attribution.issueNumbers,
        attributionSource: output.attribution.source,
        deliveryPath: output.attribution.deliveryPath,
        dependencies: output.dependencies,
      },
      delivery: {
        prNumber: output.number,
        deliveryPath: output.attribution.deliveryPath,
        declaredCloses: pr.closingIssuesReferences,
      },
      // A merge-type PR (section: null) is excluded from every output, so it must never trip the unattributed guard.
      bucketed:
        output.categorization.section !== null &&
        (output.attribution.source === 'unattributed' || output.attribution.source === 'resolutionFailed'),
      warnings,
    };
  };

  // Each PR's work is almost entirely waiting on the network, so it's worker-
  // pooled rather than serial. Results land in index-keyed slots (never
  // pushed) since completion order is arbitrary but output order must not be.
  //
  // ponytail: 3 workers, not more — `resolve()` already runs CONCURRENCY refs
  // per PR, so the two multiply; 6 here tripped GitHub's secondary rate limit
  // on burst width, not quota. Raising this wants one shared limit, not a
  // bigger number here.
  const WORKERS = 3;
  const processed = new Array<Processed | undefined>(metadata.length);
  let cursor = 0;
  try {
    await Promise.all(
      Array.from({ length: Math.min(WORKERS, metadata.length) }, async () => {
        for (let index = cursor++; index < metadata.length; index = cursor++) {
          processed[index] = await processOne(metadata[index]!);
        }
      }),
    );
  } finally {
    // Flushed even on a partial failure — those diagnostics are the most worth reading.
    for (const entry of processed) {
      if (entry) for (const warning of entry.warnings) core.warning(warning);
    }
  }

  // After attribution, since the issue set is what attribution produces —
  // includes backport-settled issues too, since kind/* visibility needs all of them.
  const wantedIssues = new Set<number>();
  for (const entry of processed) {
    if (!entry) continue;
    for (const issueNumber of entry.renderPr.issueNumbers) wantedIssues.add(issueNumber);
  }
  const issueFacts = await graphql.fetchIssueFacts([...wantedIssues]);
  core.info(`Read labels and the close event of ${issueFacts.size} of ${wantedIssues.size} referenced issue(s).`);

  const attributed: RenderPrInput[] = [];
  const unattributed: RenderPrInput[] = [];
  const issueFactsWarnings: string[] = [];
  for (const entry of processed) {
    if (!entry) continue;
    const internalKind = hiddenFromCustomerBody(
      entry.renderPr.issueNumbers.map((issueNumber) => issueFacts.get(issueNumber)?.labels ?? []),
    );
    if (internalKind) {
      issueFactsWarnings.push(
        `PR #${entry.renderPr.number}: linked issue is ${internalKind} — kept in the full asset, hidden from the customer body.`,
      );
    }
    for (const issueNumber of entry.renderPr.issueNumbers) {
      if (issueFacts.get(issueNumber)?.labelsTruncated) {
        issueFactsWarnings.push(
          `Issue #${issueNumber} has more than 20 labels — kind/* visibility could not be verified against the full label set.`,
        );
      }
    }

    const renderPr: RenderPrInput = {
      ...entry.renderPr,
      visibility: internalKind ? 'internal' : entry.renderPr.visibility,
      closesIssueNumbers: closesIssueNumbers({ ...entry.delivery, issueNumbers: entry.renderPr.issueNumbers }, issueFacts),
      // positively open only — an issue absent from the lookup is not evidence of anything
      openIssueNumbers: entry.renderPr.issueNumbers.filter((issueNumber) => issueFacts.get(issueNumber)?.closed === false),
    };
    (entry.bucketed ? unattributed : attributed).push(renderPr);
  }

  for (const warning of issueFactsWarnings) core.warning(warning);
  const auditWarnings = [...rangeReasons, ...processed.flatMap((entry) => entry?.warnings ?? []), ...issueFactsWarnings];

  const result = render(attributed, unattributed, {
    version: input.targetVersion,
    allowUnattributed: input.allowUnattributed,
    unattributedReason: input.unattributedReason,
    warnings: auditWarnings,
  });

  // Same condition render() already folded into audit.json — surfaced here
  // too so it isn't only visible to someone who goes looking at that file.
  const emptyBodyWarning = emptyCustomerBodyWarning(attributed.length > 0, result.customerBody);
  if (emptyBodyWarning) core.warning(emptyBodyWarning);

  mkdirSync(input.outputDir, { recursive: true }); // writeFileSync doesn't create the dir; recursive for a nested output-dir too

  writeFileSync(`${input.outputDir}/CHANGELOG-${input.targetVersion}.md`, result.fullAsset);
  writeFileSync(`${input.outputDir}/changelog.json`, JSON.stringify(result.changelogJson, null, 2));
  writeFileSync(`${input.outputDir}/labels.json`, JSON.stringify(result.labelsJson, null, 2));
  writeFileSync(`${input.outputDir}/audit.json`, JSON.stringify(result.auditJson, null, 2));
  writeFileSync(`${input.outputDir}/comments.json`, JSON.stringify(result.commentsJson, null, 2));
  core.setOutput('customer-body', result.customerBody);

  await core.summary // both bodies, written even when the unattributed guard trips, never skipped on failure
    .addHeading(`Release notes — ${input.targetVersion}`, 2)
    .addHeading('Customer-facing body', 3)
    .addRaw(result.customerBody)
    .addHeading('Full asset (includes internal-only sections)', 3)
    .addRaw(result.fullAsset)
    .write();

  if (result.failureReason) throw new Error(result.failureReason); // fails only AFTER every diagnostic output exists on disk

  core.info(`Generated release notes for ${input.targetVersion}: ${attributed.length} attributed PR(s).`);
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
