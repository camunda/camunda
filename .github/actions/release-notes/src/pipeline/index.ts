import { decideAttribution, evaluatePostGateAnomaly, hasEligibleRefs } from '../attribution';
import type { AttributionAnomaly, AttributionDecision, AttributionSource } from '../attribution/types';
import { BOT_CATEGORY_OVERRIDES, categorize, formatDependencyUpdates, parseDependencyUpdate, stripBackportPrefix } from '../categorize';
import type { CategorizeDecision, DependencyUpdate } from '../categorize';
import { extractSection, isOptOutTicked, parseRefs } from '../parser';
import { isLinkExemptAuthor } from '../title';
import type { ParsedRef, ResolvedRef } from '../types';

/**
 * Composes the pure attribution + categorize modules with ref resolution for
 * ONE PR, including the backport hop. Mirrors gate/index.ts: pure core
 * tested against a fake resolver, no network.
 *
 * ponytail: the hop attributes the original from its body alone, without its
 * native `closingIssuesReferences` (that field is only fetched for PRs in
 * range, and the original usually isn't) — matters only in the already-rare
 * "nothing found anywhere" case.
 */

export interface OriginalPull {
  readonly body: string;
  readonly title: string;
  readonly authorLogin?: string;
  readonly mergedAt?: string;
}

export interface PipelineResolver {
  resolveRefs(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  /** `repo`: the marker's explicit `owner/repo`, or null for same-repo. A
   *  cross-repo marker must resolve to null, never a same-numbered local PR. */
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
   *  anomaly severity only, never attribution itself. */
  readonly gateRequiredAt: string | null;
}

export interface PipelinePrOutput {
  readonly number: number;
  readonly title: string;
  readonly attribution: AttributionDecision;
  readonly categorization: CategorizeDecision;
  readonly anomaly?: AttributionAnomaly;
  /** Present only for a `deps:` PR whose bot prose parsed. */
  readonly dependencies: readonly DependencyUpdate[];
}

/** The legacy scan resolves only when earlier steps can't terminate — every
 *  ref costs an API call, and section refs would otherwise resolve twice. */
async function attributeDirectly(
  resolver: PipelineResolver,
  body: string,
  closingIssuesReferences: readonly number[],
): Promise<AttributionDecision> {
  const section = extractSection(body);
  const optOut = section ? isOptOutTicked(section) : false;
  const sectionRefs = section ? await resolver.resolveRefs(parseRefs(section)) : [];

  const needsLegacyScan = !optOut && !hasEligibleRefs(sectionRefs) && closingIssuesReferences.length === 0;
  // Unlike a section ref (deliberately listed there), a bare "#N" anywhere in
  // the body is as likely an incidental mention ("similar to #100") as a real
  // attribution — only a ref carrying an explicit keyword counts here.
  const legacyRefs = needsLegacyScan ? await resolver.resolveRefs(parseRefs(body).filter((ref) => ref.keyword !== null)) : [];

  return decideAttribution({ optOut, sectionRefs, closingIssuesReferences, legacyRefs });
}

/** The trigger for the bot-link exemption. Mirrors the gate's own
 *  failing-link outcomes, not just "nothing found". */
const UNRESOLVED_SOURCES: ReadonlySet<AttributionSource> = new Set(['unattributed', 'resolutionFailed']);

interface Attributed {
  readonly decision: AttributionDecision;
  /** The ORIGINAL's merge timestamp when the decision came from a backport
   *  hop — a post-gate backport of a pre-gate original is not a gate
   *  violation. Falls back to the backport's own timestamp otherwise. */
  readonly mergedAt: string;
}

/** Direct scan, then the backport hop, then bot link exemption last — an
 *  exempt bot that did link a real issue keeps it. See GENERATOR.md § 3 for
 *  why the hop always trusts the original over the backport's own body. */
async function attributePr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  original: () => Promise<OriginalPull | null>,
): Promise<Attributed> {
  let decision = await attributeDirectly(resolver, pr.body, pr.closingIssuesReferences);
  let mergedAt = pr.mergedAt;

  if (decision.source !== 'optOut') {
    const originalPull = await original(); // null for an ordinary PR — costs nothing
    if (originalPull) {
      const originalDecision = await attributeDirectly(resolver, originalPull.body, []);
      decision = { ...originalDecision, deliveryPath: 'backportHop' };
      mergedAt = originalPull.mergedAt ?? pr.mergedAt;
    }
  }

  if (UNRESOLVED_SOURCES.has(decision.source) && isLinkExemptAuthor(pr.authorLogin)) {
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

/** Category-detection title and display title share one lookup — an
 *  inherit-original bot's own title is garbage for both. Its author is too:
 *  a Dependabot original relies on ITS OWN `deps` override (the automation
 *  bot that carried it over has no such override), and its dependency table
 *  lives in the original's body, not the backport's — so `canonicalTitle`/
 *  `canonicalBody` (the original's, when inherited) are returned alongside
 *  for every later step that needs the PR's actual content. */
async function categorizePr(
  resolver: PipelineResolver,
  pr: PipelinePrInput,
  original: () => Promise<OriginalPull | null>,
  override: 'inherit-original' | 'deps' | undefined,
): Promise<{ displayTitle: string; canonicalTitle: string; canonicalBody: string; categorization: CategorizeDecision }> {
  const inheritedOriginal = override === 'inherit-original' ? await original() : undefined;
  const canonicalTitle = inheritedOriginal?.title ?? pr.title;
  const canonicalBody = inheritedOriginal ? inheritedOriginal.body : pr.body;
  const displayTitle = stripBackportPrefix(canonicalTitle);
  const componentLabels = pr.labels.filter((label) => label.startsWith('component/'));
  const categorization = categorize({
    title: displayTitle,
    authorLogin: inheritedOriginal?.authorLogin ?? pr.authorLogin,
    componentLabels,
    breakingChangeLabel: pr.labels.includes('BREAKING CHANGE'),
  });
  return { displayTitle, canonicalTitle, canonicalBody, categorization };
}

/**
 * The customer-facing title, in priority order: a `deps:` PR's parsed
 * "name: old → new"; else the FIRST linked issue's own title (written for a
 * release-notes reader, unlike the PR title); else the PR's own title.
 */
async function resolveDisplayTitle(
  resolver: PipelineResolver,
  canonical: { readonly title: string; readonly body: string },
  categorization: CategorizeDecision,
  attribution: AttributionDecision,
  fallbackTitle: string,
): Promise<string> {
  if (categorization.section === 'Dependency updates') {
    const updates = parseDependencyUpdate(canonical);
    if (updates.length > 0) return formatDependencyUpdates(updates);
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
  let pending: Promise<OriginalPull | null> | undefined; // memoized: attribution + inherit-original both want the same original PR
  const original = (): Promise<OriginalPull | null> =>
    (pending ??= backport ? resolver.fetchOriginalPull(backport.number, backport.repo) : Promise.resolve(null));

  const { decision: attribution, mergedAt } = await attributePr(resolver, pr, original);
  const { displayTitle, canonicalTitle, canonicalBody, categorization } = await categorizePr(resolver, pr, original, override);
  const canonical = { title: canonicalTitle, body: canonicalBody };
  const title = await resolveDisplayTitle(resolver, canonical, categorization, attribution, displayTitle);
  const anomaly = evaluatePostGateAnomaly({
    mergedAt,
    gateRequiredAt: options.gateRequiredAt,
    source: attribution.source,
  });
  const dependencies = categorization.section === 'Dependency updates' ? parseDependencyUpdate(canonical) : [];

  return { number: pr.number, title, attribution, categorization, anomaly, dependencies };
}
