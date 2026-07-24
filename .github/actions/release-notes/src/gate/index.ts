import { extractSection, isOptOutTicked, parseRefs } from '../parser';
import { decide } from '../policy';
import { isTitleExemptAuthor, lintTitle } from '../title';
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
  const optOut = isOptOutTicked(body);
  const refs = section ? parseRefs(section) : [];
  const resolved = await resolver.resolve(refs);
  return decide(resolved, optOut);
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
      // fetchPullBody returns null for a 404 target OR a cross-repo marker — the
      // resolver can only validate its own repo. Either way we cannot inherit
      // attribution, so surface the dangling marker rather than absorbing it
      // into the generic "no linked issue" message.
      const originalBody = await resolver.fetchPullBody(backport.number, backport.repo);
      deliveryPath = 'backportHop';
      if (originalBody === null) {
        link = {
          outcome: 'fail',
          code: 'unlinked-undeclared',
          reasons: [
            `Backport of ${backport.repo ? `${backport.repo}#` : '#'}${backport.number}, but that PR could not be resolved in this repo — attribution cannot be inherited.`,
            ...link.reasons,
          ],
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
                code: 'unlinked-undeclared',
                reasons: [
                  `Backport of #${backport.number}, but that PR does not link a tracked issue either.`,
                  ...original.reasons,
                ],
              };
      }
    }
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
