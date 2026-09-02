import { decideAttribution, evaluatePostGateAnomaly, hopToBackportOriginal } from '../attribution';
import type { AttributionAnomaly, AttributionDecision } from '../attribution/types';
import { BOT_CATEGORY_OVERRIDES, categorize, resolveCategorizeTitle, stripBackportPrefix } from '../categorize';
import type { CategorizeDecision } from '../categorize';
import { extractSection, isOptOutTicked, parseRefs } from '../parser';
import { isLinkExemptAuthor } from '../title';
import type { ParsedRef, ResolvedRef } from '../types';

/**
 * Composes the pure attribution + categorize modules with ref resolution for
 * ONE PR, including the backport hop. Mirrors gate/index.ts's role: the pure
 * core stays unit-tested against a fake resolver, no network, and this is the
 * only place that knows the two need to run together.
 *
 * ponytail: the hop's `fetchOriginalPull` does not carry the original's
 * native `closingIssuesReferences` (that field only comes from the batched
 * GraphQL PR-metadata phase, and the original PR is often outside the
 * current release range, so it was never fetched there) — the hop's own
 * attribution chain runs section-scan and legacy-scan only. This only matters
 * when the original ALSO has no section ref and no legacy ref, which is
 * already the rare "no attribution found anywhere" case; revisit if a real
 * release shows this producing a wrong unattributed hop.
 */

export interface PipelineResolver {
  resolveRefs(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  fetchOriginalPull(number: number): Promise<{ readonly body: string; readonly title: string; readonly authorLogin?: string } | null>;
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
  /** The branch's recorded gate watermark, or null if it hasn't required the
   *  gate yet — never affects attribution itself, only anomaly severity (D20). */
  readonly gateRequiredAt: string | null;
}

export interface PipelinePrOutput {
  readonly number: number;
  readonly title: string;
  readonly attribution: AttributionDecision;
  readonly categorization: CategorizeDecision;
  readonly anomaly?: AttributionAnomaly;
}

async function attributeDirectly(
  resolver: PipelineResolver,
  body: string,
  closingIssuesReferences: readonly number[],
): Promise<AttributionDecision> {
  const section = extractSection(body);
  const optOut = section ? isOptOutTicked(section) : false;
  const sectionRefs = section ? await resolver.resolveRefs(parseRefs(section)) : [];
  const legacyRefs = await resolver.resolveRefs(parseRefs(body));
  return decideAttribution({ optOut, sectionRefs, closingIssuesReferences, legacyRefs });
}

/**
 * Bot link exemption (mirrors the gate's `isLinkExemptAuthor` — currently
 * `renovate[bot]` only): a dependency-bump PR opened from the bot's own
 * template will never carry a section ref or a checkbox tick, and pre-gate
 * there was no opt-out mechanism for it to use even if it wanted to. Applied
 * LAST, only to a still-unattributed decision after the direct scan and the
 * backport hop have both had their chance — an exempt bot that DID link a
 * real issue keeps that real attribution, never overridden by the exemption.
 */
async function attributePr(resolver: PipelineResolver, pr: PipelinePrInput): Promise<AttributionDecision> {
  let decision = await attributeDirectly(resolver, pr.body, pr.closingIssuesReferences);

  if (decision.source === 'unattributed') {
    const backport = parseRefs(pr.body).find((ref) => ref.kind === 'backport');
    if (backport) {
      const original = await resolver.fetchOriginalPull(backport.number);
      if (original) {
        const originalDecision = await attributeDirectly(resolver, original.body, []);
        decision = hopToBackportOriginal(originalDecision);
      }
    }
  }

  if (decision.source === 'unattributed' && isLinkExemptAuthor(pr.authorLogin)) {
    return {
      source: 'botExempt',
      issueNumbers: [],
      deliveryPath: 'direct',
      reasons: [`Author ${pr.authorLogin} is exempt from the PR-issue link requirement.`],
    };
  }

  return decision;
}

/**
 * Resolves both the category-detection title AND the customer-facing display
 * title from the same lookup — an inherit-original bot's own title is
 * garbage for both purposes, so whichever title categorize() uses to decide
 * the section is also the one worth showing the reader, with the
 * `[Backport ...]` marker itself stripped either way (noise, not a fact the
 * customer needs).
 */
async function categorizePr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
): Promise<{ displayTitle: string; categorization: CategorizeDecision }> {
  const override = pr.authorLogin ? BOT_CATEGORY_OVERRIDES[pr.authorLogin] : undefined;
  let originalTitle: string | undefined;
  if (override === 'inherit-original') {
    const backport = parseRefs(pr.body).find((ref) => ref.kind === 'backport');
    if (backport) originalTitle = (await resolver.fetchOriginalPull(backport.number))?.title;
  }

  const resolvedTitle = resolveCategorizeTitle({ title: pr.title, authorLogin: pr.authorLogin, originalTitle });
  const displayTitle = stripBackportPrefix(resolvedTitle);
  const componentLabels = pr.labels.filter((label) => label.startsWith('component/'));
  const breakingChangeLabel = pr.labels.includes('BREAKING CHANGE');
  const categorization = categorize({ title: displayTitle, authorLogin: pr.authorLogin, componentLabels, breakingChangeLabel });
  return { displayTitle, categorization };
}

export async function processPr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  options: PipelineOptions,
): Promise<PipelinePrOutput> {
  const attribution = await attributePr(resolver, pr);
  const { displayTitle, categorization } = await categorizePr(resolver, pr);
  const anomaly = evaluatePostGateAnomaly({
    mergedAt: pr.mergedAt,
    gateRequiredAt: options.gateRequiredAt,
    source: attribution.source,
  });

  return { number: pr.number, title: displayTitle, attribution, categorization, anomaly };
}
