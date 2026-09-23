import type { OriginalPull, PipelineResolver } from '../pipeline';
import { prioritizeAndCap } from '../resolver';
import type { ParsedRef, ResolvedRef } from '../types';
import type { ClassifiedRef } from './index';

/** Serves the per-PR phase from classifications learned in one bulk pass, so
 *  it makes almost no requests of its own. Generator-only. */
export interface RefResolver {
  resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  fetchOriginalPull(number: number, repo: string | null): Promise<OriginalPull | null>;
  fetchIssueTitle(number: number): Promise<string | null>;
}

export function buildPipelineResolver(
  rest: RefResolver,
  warmRefs: ReadonlyMap<number, ClassifiedRef>,
): PipelineResolver {
  // Cross-repo refs are never pre-warmed — the REST path classifies those without an API call.
  const sameRepoNumber = (ref: ParsedRef): number | null => (ref.repo === null ? ref.number : null);

  return {
    async resolveRefs(refs) {
      const capped = prioritizeAndCap(refs); // same cap/priority as the REST resolver — imported, not restated
      const cold = capped.filter((ref) => {
        const number = sameRepoNumber(ref);
        return number === null || !warmRefs.has(number);
      });
      const fresh = cold.length > 0 ? await rest.resolve(cold) : [];
      // Keyed by position, not number: a body can cite the same issue twice,
      // and keying by number would collapse the two occurrences into one.
      const freshByPosition = new Map(fresh.map((ref) => [ref.index, ref]));

      return capped
        .map((ref): ResolvedRef => {
          const number = sameRepoNumber(ref);
          const warm = number === null ? undefined : warmRefs.get(number);
          if (warm) return { ...ref, target: warm.target, crossRepo: false };
          return freshByPosition.get(ref.index) ?? { ...ref, target: 'missing', crossRepo: ref.repo !== null };
        })
        .sort((first, second) => first.index - second.index);
    },
    fetchOriginalPull: (number, repo) => rest.fetchOriginalPull(number, repo),
    fetchIssueTitle: async (number) => warmRefs.get(number)?.title ?? rest.fetchIssueTitle(number),
  };
}
