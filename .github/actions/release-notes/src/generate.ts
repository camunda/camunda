import { writeFileSync } from 'node:fs';
import * as core from './gha';
import { processPr } from './pipeline';
import type { PipelineResolver } from './pipeline';
import { resolveBaselineStrategy, resolveCommitsToPrs } from './range';
import { resolveBaselineRef, walkFirstParent } from './range/walk';
import type { RenderPrInput } from './render';
import { render } from './render';
import { extractSection, parseRefs } from './parser';
import { GithubGraphqlResolver } from './resolve';
import type { AssociatedPr, ClassifiedRef, PrMetadata } from './resolve';
import { buildPipelineResolver } from './resolve/warm';
import { GithubResolver, prioritizeAndCap } from './resolver';

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
  const warmRefs = new Map<number, ClassifiedRef>();
  const pipelineResolver = buildPipelineResolver(restResolver, warmRefs);

  const strategy = resolveBaselineStrategy(input.targetVersion);
  const baseline = resolveBaselineRef(process.cwd(), strategy, input.targetVersion);
  const walked = walkFirstParent(process.cwd(), baseline, input.targetVersion);
  core.info(`Range ${baseline}..${input.targetVersion}: ${walked.length} first-parent commits.`);

  const rangeShas = new Set(walked.map((commit) => commit.sha));

  // GitHub writes the pull request number into the subject of the commit it
  // squashes onto the branch, so for nearly every commit the mapping is already
  // in hand: 3662 of 8.9.0's 3694, and 5486 of 8.8.0's 5514. Asking
  // `associatedPullRequests` to rediscover it means walking branch history for
  // every commit — 148 requests for one minor, the slowest phase of the run.
  //
  // Derived, never trusted: the candidate is confirmed against the pull
  // request's own `mergeCommit`, which must BE this commit. That is a stronger
  // signal than `associatedPullRequests`, which reports every pull request
  // whose branch history contains the commit and is what once credited a
  // release to its own merge-back. Anything unconfirmed — no number in the
  // subject, unknown pull request, no merge commit, or a merge commit that is
  // some other commit — falls back to the original query, so a wrong guess
  // cannot become a wrong attribution.
  const candidateBySha = new Map<string, number>();
  for (const commit of walked) {
    const match = /\(#(\d+)\)\s*$/.exec(commit.message);
    if (match) candidateBySha.set(commit.sha, Number(match[1]));
  }

  const metaByNumber = new Map<number, PrMetadata>();
  for (const meta of await graphql.fetchPrMetadata([...new Set(candidateBySha.values())])) {
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

  // Only the pull requests the fallback discovered are still unfetched.
  for (const meta of await graphql.fetchPrMetadata(prNumbers.filter((number) => !metaByNumber.has(number)))) {
    metaByNumber.set(meta.number, meta);
  }
  // Keyed off prNumbers, which is in walk order, so the output stays stable.
  const metadata = prNumbers.map((number) => metaByNumber.get(number)).filter((meta): meta is PrMetadata => meta !== undefined);

  // Every reference the per-pull-request phase can ask about, learned in one
  // pass. The pipeline resolves the "Related issues" section and, when that
  // yields nothing, scans the whole body — so pre-warm the union of both,
  // each capped by the same policy the resolver applies per call.
  const wanted = new Set<number>();
  for (const pr of metadata) {
    const section = extractSection(pr.body);
    for (const refs of [section ? parseRefs(section) : [], parseRefs(pr.body)]) {
      for (const ref of prioritizeAndCap(refs)) {
        if (ref.repo === null) wanted.add(ref.number);
      }
    }
  }
  for (const [number, classified] of await graphql.classifyRefs([...wanted])) {
    warmRefs.set(number, classified);
  }
  core.info(`Pre-classified ${warmRefs.size} distinct references in ${Math.ceil(wanted.size / 100)} requests.`);

  /** One pull request's attribution, plus the warnings it produced. Warnings are
   *  collected rather than emitted so the log stays in walk order no matter
   *  which worker finishes first — an interleaved audit log is unreadable and,
   *  worse, differs between runs of the same release. */
  interface Processed {
    readonly renderPr: RenderPrInput;
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
        // A backport hop delivers via THIS PR's merge, but the backport bot never
        // writes a closing keyword — closingIssuesReferences is always empty for
        // it, so the general signal below would under-report every single one.
        closesIssueNumbers:
          output.attribution.deliveryPath === 'backportHop'
            ? output.attribution.issueNumbers
            : output.attribution.issueNumbers.filter((n) => pr.closingIssuesReferences.includes(n)),
        attributionSource: output.attribution.source,
      },
      // A `merge`-type PR (section: null) is excluded from every render() output
      // regardless of attribution, so it must never trip the unattributed guard.
      bucketed:
        output.categorization.section !== null &&
        (output.attribution.source === 'unattributed' || output.attribution.source === 'resolutionFailed'),
      warnings,
    };
  };

  // Each pull request's work is independent and almost entirely waiting on the
  // network, so a serial loop spends a minor release's runtime idle: 8.9.0 took
  // ~35 minutes here. Results land in index-keyed slots, never pushed, because
  // completion order is arbitrary while the release notes' order must not be.
  //
  // ponytail: 3 workers, not more. `resolve()` already runs up to CONCURRENCY
  // refs per pull request, so the two limits multiply. Six here — about 30
  // requests in flight — tripped GitHub's SECONDARY rate limit on 8.9.0, which
  // fires on concurrency rather than volume: the primary counter still read
  // 5000/5000 when it hit. The ceiling is burst width, not quota, so the fix is
  // fewer in flight rather than a bigger budget. Raising this wants one shared
  // limit across both levels, not a bigger number here.
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
    // Deferring warnings to keep them in walk order must not mean losing them
    // when the run dies partway: a failed run's diagnostics are the ones most
    // worth reading.
    for (const entry of processed) {
      if (entry) for (const warning of entry.warnings) core.warning(warning);
    }
  }

  const attributed: RenderPrInput[] = [];
  const unattributed: RenderPrInput[] = [];
  for (const entry of processed) {
    if (!entry) continue;
    (entry.bucketed ? unattributed : attributed).push(entry.renderPr);
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
