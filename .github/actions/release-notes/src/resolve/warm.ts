import type { OriginalPull, PipelineResolver } from '../pipeline';
import { extractSection, parseRefs } from '../parser';
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

/** A full-URL ref to this repo parses with `repo` set, so `null` alone misses it. */
export function isOwnRepo(repo: string | null, ownRepo: string): boolean {
  return repo === null || repo.toLowerCase() === ownRepo.toLowerCase();
}

/** Every own-repo number the pipeline might resolve for these bodies — section
 *  refs plus full-body refs, each capped by the policy the resolver applies. */
export function prewarmNumbers(bodies: readonly string[], ownRepo: string): Set<number> {
  const wanted = new Set<number>();
  for (const body of bodies) {
    const section = extractSection(body);
    for (const refs of [section ? parseRefs(section) : [], parseRefs(body)]) {
      for (const ref of prioritizeAndCap(refs)) {
        if (isOwnRepo(ref.repo, ownRepo)) wanted.add(ref.number);
      }
    }
  }
  return wanted;
}

/** The own-repo PRs these bodies are backports of, deduped — the same marker
 *  the pipeline's hop follows. A cross-repo marker never resolves here. */
export function backportTargets(bodies: readonly string[], ownRepo: string): number[] {
  const targets = new Set<number>();
  for (const body of bodies) {
    const marker = parseRefs(body).find((ref) => ref.kind === 'backport');
    if (marker && isOwnRepo(marker.repo, ownRepo)) targets.add(marker.number);
  }
  return [...targets];
}

/** `originals` holds the backport originals fetched in bulk; anything absent
 *  (not prefetched, or not a merged PR) still goes to REST. */
export function buildPipelineResolver(
  rest: RefResolver,
  warmRefs: ReadonlyMap<number, ClassifiedRef>,
  ownRepo: string,
  originals: ReadonlyMap<number, OriginalPull> = new Map(),
): PipelineResolver {
  // Cross-repo refs are never pre-warmed — the REST path classifies those without an API call.
  const sameRepoNumber = (ref: ParsedRef): number | null => (isOwnRepo(ref.repo, ownRepo) ? ref.number : null);

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
          return freshByPosition.get(ref.index) ?? { ...ref, target: 'missing', crossRepo: !isOwnRepo(ref.repo, ownRepo) };
        })
        .sort((first, second) => first.index - second.index);
    },
    fetchOriginalPull: async (number, repo) =>
      (isOwnRepo(repo, ownRepo) ? originals.get(number) : undefined) ?? rest.fetchOriginalPull(number, repo),
    fetchIssueTitle: async (number) => warmRefs.get(number)?.title ?? rest.fetchIssueTitle(number),
  };
}
