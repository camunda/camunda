import type { OriginalPull, PipelineResolver } from '../pipeline';
import { prioritizeAndCap } from '../resolver';
import type { ParsedRef, ResolvedRef } from '../types';
import type { ClassifiedRef } from './index';

/**
 * Serves the per-pull-request phase from classifications learned in one bulk
 * pass, so that phase makes almost no requests of its own. Generator-only: the
 * gate resolves one pull request's refs and has nothing to pre-warm from.
 */
/** The subset of GithubResolver this wrapper needs, so a test can supply a fake. */
export interface RefResolver {
  resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
  fetchOriginalPull(number: number, repo: string | null): Promise<OriginalPull | null>;
  fetchIssueTitle(number: number): Promise<string | null>;
}

/**
 * The per-pull-request phase's view of the API, served from classifications
 * learned in bulk beforehand so the phase itself makes almost no requests.
 * Anything not pre-warmed — a cross-repo ref, or one the bulk pass missed —
 * falls through to the REST resolver unchanged.
 */
export function buildPipelineResolver(
  rest: RefResolver,
  warmRefs: ReadonlyMap<number, ClassifiedRef>,
): PipelineResolver {
  /** Only same-repo refs are pre-warmed: the REST path classifies a cross-repo
   *  one without an API call, so there is nothing to save and nothing to
   *  restate about what counts as same-repo. */
  const sameRepoNumber = (ref: ParsedRef): number | null => (ref.repo === null ? ref.number : null);

  return {
    async resolveRefs(refs) {
      // The SAME cap and priority the REST resolver applies — imported, not
      // restated, so the two can never disagree about which refs survive.
      const capped = prioritizeAndCap(refs);
      const cold = capped.filter((ref) => {
        const number = sameRepoNumber(ref);
        return number === null || !warmRefs.has(number);
      });
      const fresh = cold.length > 0 ? await rest.resolve(cold) : [];
      // Keyed by the ref's own position, not by its number: a body may cite the
      // same issue twice — #42118 cites #41769 at index 1 and again at 2680 —
      // and keying by number collapses the two, leaving one occurrence carrying
      // the other's index. Sorting by index then silently reorders the refs,
      // which changes which issue is "first" and so which issue the entry is
      // grouped and titled by.
      const freshByPosition = new Map(fresh.map((ref) => [ref.index, ref]));

      return capped
        .map((ref): ResolvedRef => {
          const number = sameRepoNumber(ref);
          const warm = number === null ? undefined : warmRefs.get(number);
          if (warm) return { ...ref, target: warm.target, crossRepo: false };
          // Never invent an answer: anything not pre-warmed came back from the
          // REST path above, and if even that has no verdict the ref is left to
          // the same 'missing' the resolver itself would report.
          return freshByPosition.get(ref.index) ?? { ...ref, target: 'missing', crossRepo: ref.repo !== null };
        })
        .sort((first, second) => first.index - second.index);
    },
    fetchOriginalPull: (number, repo) => rest.fetchOriginalPull(number, repo),
    fetchIssueTitle: async (number) => {
      const warm = warmRefs.get(number);
      // A pre-warmed classification already carries the title, so the separate
      // per-issue fetch this phase used to make is redundant for those.
      return warm ? warm.title : rest.fetchIssueTitle(number);
    },
  };
}
