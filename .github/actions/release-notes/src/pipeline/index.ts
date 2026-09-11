import { decideAttribution, evaluatePostGateAnomaly, hopToBackportOriginal } from '../attribution';
import type { AttributionAnomaly, AttributionDecision } from '../attribution/types';
import { BOT_CATEGORY_OVERRIDES, categorize, resolveCategorizeTitle } from '../categorize';
import type { CategorizeDecision } from '../categorize';
import { extractSection, isOptOutTicked, parseRefs } from '../parser';
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

async function attributePr(resolver: PipelineResolver, pr: PipelinePrInput): Promise<AttributionDecision> {
  const direct = await attributeDirectly(resolver, pr.body, pr.closingIssuesReferences);
  if (direct.source !== 'unattributed') return direct;

  const backport = parseRefs(pr.body).find((ref) => ref.kind === 'backport');
  if (!backport) return direct;

  const original = await resolver.fetchOriginalPull(backport.number);
  if (!original) return direct;

  const originalDecision = await attributeDirectly(resolver, original.body, []);
  return hopToBackportOriginal(originalDecision);
}

async function categorizePr(resolver: PipelineResolver, pr: PipelinePrInput): Promise<CategorizeDecision> {
  const override = pr.authorLogin ? BOT_CATEGORY_OVERRIDES[pr.authorLogin] : undefined;
  let originalTitle: string | undefined;
  if (override === 'inherit-original') {
    const backport = parseRefs(pr.body).find((ref) => ref.kind === 'backport');
    if (backport) originalTitle = (await resolver.fetchOriginalPull(backport.number))?.title;
  }

  const title = resolveCategorizeTitle({ title: pr.title, authorLogin: pr.authorLogin, originalTitle });
  const componentLabels = pr.labels.filter((label) => label.startsWith('component/'));
  const breakingChangeLabel = pr.labels.includes('BREAKING CHANGE');
  return categorize({ title, authorLogin: pr.authorLogin, componentLabels, breakingChangeLabel });
}

export async function processPr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  options: PipelineOptions,
): Promise<PipelinePrOutput> {
  const attribution = await attributePr(resolver, pr);
  const categorization = await categorizePr(resolver, pr);
  const anomaly = evaluatePostGateAnomaly({
    mergedAt: pr.mergedAt,
    gateRequiredAt: options.gateRequiredAt,
    source: attribution.source,
  });

  return { number: pr.number, title: pr.title, attribution, categorization, anomaly };
}
