import { decideAttribution, evaluatePostGateAnomaly, hasEligibleRefs } from '../attribution';
import type { AttributionAnomaly, AttributionDecision, AttributionSource } from '../attribution/types';
import { BOT_CATEGORY_OVERRIDES, categorize, parseDependencyUpdate, stripBackportPrefix } from '../categorize';
import type { CategorizeDecision } from '../categorize';
import { extractSection, isOptOutTicked, parseRefs } from '../parser';
import { isLinkExemptAuthor } from '../title';
import type { ParsedRef, ResolvedRef } from '../types';

/**
 * Composes the pure attribution + categorize modules with ref resolution for
 * ONE PR, including the backport hop — the only place that knows the two run
 * together. Mirrors gate/index.ts: the pure core is tested against a fake
 * resolver, no network.
 *
 * ponytail: the hop attributes the original from its body alone (section +
 * legacy scan), without the original's native `closingIssuesReferences` — that
 * field comes only from the batched GraphQL metadata phase, and the original is
 * usually outside the release range. Only matters when the original has no ref
 * of either kind, i.e. the already-rare "nothing found anywhere" case.
 */

export interface OriginalPull {
  readonly body: string;
  readonly title: string;
  readonly authorLogin?: string;
  readonly mergedAt?: string;
}

export interface PipelineResolver {
  resolveRefs(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  /** `repo` is the backport marker's explicit `owner/repo` prefix, or null for
   *  a same-repo marker. A cross-repo marker must resolve to null — a same-
   *  numbered PR in THIS repo would otherwise be inherited by mistake. */
  fetchOriginalPull(number: number, repo: string | null): Promise<OriginalPull | null>;
  fetchIssueTitle(number: number): Promise<string | null>;
}

export interface PipelinePrInput {
  readonly number: number;
  readonly title: string;
  readonly body: string;
  readonly authorLogin?: string;
  readonly mergedAt: string;
  readonly labels: readonly string[];
  readonly closingIssuesReferences: readonly number[];
}

export interface PipelineOptions {
  /** The branch's gate watermark, or null if it isn't gated yet — affects
   *  anomaly severity only, never attribution itself (D20). */
  readonly gateRequiredAt: string | null;
}

export interface PipelinePrOutput {
  readonly number: number;
  readonly title: string;
  readonly attribution: AttributionDecision;
  readonly categorization: CategorizeDecision;
  readonly anomaly?: AttributionAnomaly;
}

/**
 * The legacy body-wide scan is the chain's last step, so its refs are only
 * resolved when the earlier steps cannot terminate — an opt-out, an eligible
 * section ref, or a native reference all decide the outcome without it. Every
 * ref costs an API call, and the section refs would otherwise be resolved a
 * second time as part of the body they live in.
 */
async function attributeDirectly(
  resolver: PipelineResolver,
  body: string,
  closingIssuesReferences: readonly number[],
): Promise<AttributionDecision> {
  const section = extractSection(body);
  const optOut = section ? isOptOutTicked(section) : false;
  const sectionRefs = section ? await resolver.resolveRefs(parseRefs(section)) : [];

  const needsLegacyScan = !optOut && !hasEligibleRefs(sectionRefs) && closingIssuesReferences.length === 0;
  const legacyRefs = needsLegacyScan ? await resolver.resolveRefs(parseRefs(body)) : [];

  return decideAttribution({ optOut, sectionRefs, closingIssuesReferences, legacyRefs });
}

/** Attribution outcomes with nothing further to try directly — eligible for
 *  the backport hop and the bot-link exemption. Mirrors the gate's own
 *  hop trigger (any failing link outcome, not just "nothing found"). */
const HOPPABLE_SOURCES: ReadonlySet<AttributionSource> = new Set(['unattributed', 'resolutionFailed']);

interface Attributed {
  readonly decision: AttributionDecision;
  /** The merge timestamp the anomaly rule keys on: the ORIGINAL's when the
   *  decision came from a backport hop, because a post-gate backport of a
   *  pre-gate original is not a gate violation (D20). Falls back to the
   *  backport's own timestamp if the original's is unavailable. */
  readonly mergedAt: string;
}

/**
 * Direct scan, then the backport hop (inheriting the original's decision,
 * C7/V2), then the bot link exemption LAST — an exempt bot that did link a real
 * issue keeps that attribution rather than being overridden by the exemption.
 */
async function attributePr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  original: () => Promise<OriginalPull | null>,
): Promise<Attributed> {
  let decision = await attributeDirectly(resolver, pr.body, pr.closingIssuesReferences);
  let mergedAt = pr.mergedAt;

  if (HOPPABLE_SOURCES.has(decision.source)) {
    const originalPull = await original();
    if (originalPull) {
      const originalDecision = await attributeDirectly(resolver, originalPull.body, []);
      decision = { ...originalDecision, deliveryPath: 'backportHop' };
      mergedAt = originalPull.mergedAt ?? pr.mergedAt;
    }
  }

  if (HOPPABLE_SOURCES.has(decision.source) && isLinkExemptAuthor(pr.authorLogin)) {
    return {
      decision: {
        source: 'botExempt',
        issueNumbers: [],
        deliveryPath: 'direct',
        reasons: [`Author ${pr.authorLogin} is exempt from the PR-issue link requirement.`],
      },
      mergedAt,
    };
  }

  return { decision, mergedAt };
}

/**
 * The category-detection title and the display title come from the same lookup:
 * an inherit-original bot's own title is garbage for both purposes. The
 * `[Backport ...]` marker is stripped either way — noise for the customer.
 */
async function categorizePr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  original: () => Promise<OriginalPull | null>,
  override: 'inherit-original' | 'deps' | undefined,
): Promise<{ displayTitle: string; categorization: CategorizeDecision }> {
  const inherited = override === 'inherit-original' ? (await original())?.title : undefined;
  const displayTitle = stripBackportPrefix(inherited ?? pr.title);
  const componentLabels = pr.labels.filter((label) => label.startsWith('component/'));
  const categorization = categorize({
    title: displayTitle,
    authorLogin: pr.authorLogin,
    componentLabels,
    breakingChangeLabel: pr.labels.includes('BREAKING CHANGE'),
  });
  return { displayTitle, categorization };
}

/**
 * The customer-facing title, in priority order: a `deps:` PR's parsed
 * "name: old → new"; else the FIRST linked issue's own title (written for a
 * release-notes reader, unlike the PR title); else the PR's own title.
 */
async function resolveDisplayTitle(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  categorization: CategorizeDecision,
  attribution: AttributionDecision,
  fallbackTitle: string,
): Promise<string> {
  if (categorization.section === 'Dependency updates') {
    const dependencyLine = parseDependencyUpdate({ title: pr.title, body: pr.body });
    if (dependencyLine) return dependencyLine;
  }

  const [primaryIssue] = attribution.issueNumbers;
  if (primaryIssue !== undefined) {
    const issueTitle = await resolver.fetchIssueTitle(primaryIssue);
    if (issueTitle) return issueTitle;
  }

  return fallbackTitle;
}

export async function processPr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  options: PipelineOptions,
): Promise<PipelinePrOutput> {
  const backport = parseRefs(pr.body).find((ref) => ref.kind === 'backport');
  const override = pr.authorLogin ? BOT_CATEGORY_OVERRIDES[pr.authorLogin] : undefined;
  // Both the attribution hop and the inherit-original title want the same
  // original PR — fetch it at most once per PR, and only if one of them asks.
  let pending: Promise<OriginalPull | null> | undefined;
  const original = (): Promise<OriginalPull | null> =>
    (pending ??= backport ? resolver.fetchOriginalPull(backport.number, backport.repo) : Promise.resolve(null));

  const { decision: attribution, mergedAt } = await attributePr(resolver, pr, original);
  const { displayTitle, categorization } = await categorizePr(resolver, pr, original, override);
  const title = await resolveDisplayTitle(resolver, pr, categorization, attribution, displayTitle);
  const anomaly = evaluatePostGateAnomaly({
    mergedAt,
    gateRequiredAt: options.gateRequiredAt,
    source: attribution.source,
  });

  return { number: pr.number, title, attribution, categorization, anomaly };
}
