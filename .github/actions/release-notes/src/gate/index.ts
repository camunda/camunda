import { extractSection, isOptOutTicked, parseRefs } from '../parser';
import { decide } from '../policy';
import { isLinkExemptAuthor, isTitleExemptAuthor, lintTitle } from '../title';
import type { DeliveryPath, GateCheck, GateOutcome, ParsedRef, PolicyDecision, ResolvedRef } from '../types';

/**
 * Composes the pure parser/policy/title pieces with the network resolver:
 * the PR-issue link check (with a backport-hop fallback) plus the title
 * check. Depends only on GateResolver, so the hop is unit-tested with a
 * fake resolver, no network.
 */

/** The slice of the resolver the gate needs (GithubResolver satisfies it). */
export interface GateResolver {
  resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  /** The original PR's body for a backport hop. `repo` is the marker's
   *  explicit `owner/repo`, or null for same-repo; null back for cross-repo. */
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
  // Scoped to the section, not the whole body — a stray ticked box outside
  // "Related issues" must not pass a PR whose actual section is empty.
  const optOut = section ? isOptOutTicked(section) : false;
  const refs = section ? parseRefs(section) : [];
  const resolved = await resolver.resolve(refs);
  return decide(resolved, optOut);
}

/** Why a `Backport of #N` marker couldn't be followed, so the author sees
 *  the actual problem rather than a generic "no linked issue". */
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
  // A backport PR passes on its own section if it has one; otherwise (bot
  // backports carry only `Backport of #N`) it inherits the ORIGINAL PR's link.
  let deliveryPath: DeliveryPath = 'direct';
  let link = await evaluateLink(resolver, input.body);

  // Hop only for a genuinely undeclared link — a pr-ref-in-section failure
  // (the section itself links a PR) is a hard error the backport marker must
  // not silently override.
  if (link.outcome === 'fail' && link.code === 'unlinked-undeclared') {
    const backport = parseRefs(input.body).find((ref) => ref.kind === 'backport');
    if (backport) {
      deliveryPath = 'backportHop';
      // Only a same-repo pull request needs its body fetched.
      const [resolved] = await resolver.resolve([backport]);
      const originalBody =
        resolved?.target === 'pullRequest' && !resolved.crossRepo
          ? await resolver.fetchPullBody(backport.number, backport.repo)
          : null;
      if (originalBody === null) {
        // Speak to the marker itself — the generic section advice is noise here.
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
                code: original.code, // original's actual code, not one fixed code — the two failures need different fixes
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

  // Bot link exemption (Renovate). After the hop, only on a still-failing
  // link — a fallback, never a bypass: an explicit link always wins.
  if (link.outcome === 'fail' && isLinkExemptAuthor(input.authorLogin)) {
    link = {
      outcome: 'pass',
      code: 'bot-exempt',
      reasons: [`Author ${input.authorLogin} is exempt from the PR-issue link check.`],
    };
  }

  const checks: GateCheck[] = [{ label: 'PR-issue link', outcome: link.outcome, reasons: [...link.reasons] }];

  // --- Title lint (skipped for bot authors; link/marker still checked) ---
  if (!isTitleExemptAuthor(input.authorLogin)) {
    const title = lintTitle(input.title);
    checks.push({ label: 'Title', outcome: title.outcome, reasons: [...title.reasons] });
  }

  const outcome = checks.every((check) => check.outcome === 'pass') ? 'pass' : 'fail';
  return { outcome, checks, deliveryPath, link };
}
