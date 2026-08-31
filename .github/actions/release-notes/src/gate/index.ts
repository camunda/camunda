import { extractSection, isOptOutTicked, parseRefs } from '../parser';
import { decide } from '../policy';
import { isLinkExemptAuthor, isTitleExemptAuthor, lintTitle } from '../title';
import type { DeliveryPath, GateCheck, GateOutcome, ParsedRef, PolicyDecision, ResolvedRef } from '../types';

/**
 * Composes the pure parser/policy/title pieces with the network resolver into a
 * single gate outcome: the PR-issue link check (with a backport-hop fallback)
 * plus the title check. Kept separate from the entrypoint and depending only on
 * GateResolver, so the orchestration — including the hop — is unit-tested with a
 * fake resolver, no network.
 */

/** The slice of the resolver the gate needs (GithubResolver satisfies it). */
export interface GateResolver {
  resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  /** Fetch the original PR's body for a backport hop. `repo` is the marker's
   *  explicit `owner/repo` prefix, or null for a same-repo marker; the resolver
   *  returns null for a cross-repo marker (it can only validate its own repo). */
  fetchPullBody(number: number, repo: string | null): Promise<string | null>;
}

export interface GateInput {
  readonly body: string;
  readonly title: string;
  readonly authorLogin?: string;
}

/** Evaluate the PR-issue link for one PR body: section refs + opt-out. */
async function evaluateLink(resolver: GateResolver, body: string): Promise<PolicyDecision> {
  const section = extractSection(body);
  // Scoped to the same section as refs: the opt-out checkbox lives in the PR
  // template's "Related issues" block, not just anywhere in the body — a
  // missing/renamed heading must not let a stray ticked box elsewhere pass
  // a PR whose actual section was never filled in.
  const optOut = section ? isOptOutTicked(section) : false;
  const refs = section ? parseRefs(section) : [];
  const resolved = await resolver.resolve(refs);
  return decide(resolved, optOut);
}

/**
 * Explain why a `Backport of #N` marker could not be followed to an original
 * PR, so the author sees the actual problem rather than a generic "no linked
 * issue". Takes the caller's classification of the ref rather than resolving
 * it again — the caller already has it, to decide whether fetching the body
 * is worth doing at all.
 */
function unresolvableBackportReason(backport: ParsedRef, resolved: ResolvedRef | undefined): string {
  if (resolved?.crossRepo) {
    return `Backport of ${backport.repo}#${backport.number} points to another repository — attribution can only be inherited from a pull request in this repo.`;
  }
  if (resolved?.target === 'issue') {
    return `Backport of #${backport.number} points to an issue, not a pull request — a backport marker must reference the original PR.`;
  }
  return `Backport of #${backport.number} does not resolve to a pull request in this repo — attribution cannot be inherited.`;
}

export async function evaluateGate(resolver: GateResolver, input: GateInput): Promise<GateOutcome> {
  // --- PR-issue link, with a backport-hop fallback (C7/V2) ---
  // A backport PR passes on its own section if it has one (manual template);
  // otherwise (bot backports carry only `Backport of #N`, no section) it passes
  // by inheriting the ORIGINAL PR's attribution.
  let deliveryPath: DeliveryPath = 'direct';
  let link = await evaluateLink(resolver, input.body);

  // Only hop for a genuinely undeclared link. A `pr-ref-in-section` failure is a
  // hard error (the section itself links a PR) — an unrelated `Backport of #N`
  // marker must not silently discard it and flip the gate to pass.
  if (link.outcome === 'fail' && link.code === 'unlinked-undeclared') {
    const backport = parseRefs(input.body).find((ref) => ref.kind === 'backport');
    if (backport) {
      deliveryPath = 'backportHop';
      // Only a same-repo pull request needs its body fetched — a cross-repo,
      // missing, or issue-not-PR target already has everything the failure
      // message needs from the classification alone.
      const [resolved] = await resolver.resolve([backport]);
      const originalBody =
        resolved?.target === 'pullRequest' && !resolved.crossRepo
          ? await resolver.fetchPullBody(backport.number, backport.repo)
          : null;
      if (originalBody === null) {
        // The marker is the PR's stated attribution path, so speak to the marker
        // only — the generic "add a closing keyword / tick opt-out" section advice
        // is irrelevant for a backport PR and would just be noise.
        link = {
          outcome: 'fail',
          code: 'unlinked-undeclared',
          reasons: [unresolvableBackportReason(backport, resolved)],
        };
      } else {
        const original = await evaluateLink(resolver, originalBody);
        link =
          original.outcome === 'pass'
            ? {
                outcome: 'pass',
                code: original.code,
                reasons: [`Backport of #${backport.number} — inherits that PR's attribution (${original.code}).`],
              }
            : {
                outcome: 'fail',
                // A pr-ref-in-section failure and an unlinked-undeclared one
                // point the author at different fixes, so the hop reports the
                // original PR's actual code rather than one fixed code for
                // every failure reason.
                code: original.code,
                reasons: [
                  original.code === 'pr-ref-in-section'
                    ? `Backport of #${backport.number}, but that PR's section links a pull request, not an issue.`
                    : `Backport of #${backport.number}, but that PR does not link a tracked issue either.`,
                  ...original.reasons,
                ],
              };
      }
    }
  }

  // Bot link exemption (Renovate). Applied AFTER the hop and only to a still
  // failing link, so it is a fallback and never a bypass: a bot PR that does
  // link an issue keeps its real code, and the hop above still runs for the
  // backport bot. An explicit link therefore always wins over the exemption.
  if (link.outcome === 'fail' && isLinkExemptAuthor(input.authorLogin)) {
    link = {
      outcome: 'pass',
      code: 'bot-exempt',
      reasons: [`Author ${input.authorLogin} is exempt from the PR-issue link check.`],
    };
  }

  const checks: GateCheck[] = [{ label: 'PR-issue link', outcome: link.outcome, reasons: [...link.reasons] }];

  // --- Title lint (D16: skipped for bot authors; link/marker still checked) ---
  if (!isTitleExemptAuthor(input.authorLogin)) {
    const title = lintTitle(input.title);
    checks.push({ label: 'Title', outcome: title.outcome, reasons: [...title.reasons] });
  }

  const outcome = checks.every((check) => check.outcome === 'pass') ? 'pass' : 'fail';
  return { outcome, checks, deliveryPath, link };
}
