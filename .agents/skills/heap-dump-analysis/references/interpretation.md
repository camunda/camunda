# Reading the results

The DRG cache pack comes from a DRG cache incident.
Another cause of OOM needs ad-hoc queries, built the same way ([queries.md](queries.md)).

## Five questions

Answer them in order, each with a number from the CSVs, and write the report from the
template in `SKILL.md`.

1. **Was the heap full?** Compare the reachable heap (sum of shallow sizes in `histogram`)
   with the max heap: `-Xmx`, otherwise the pod memory limit x `-XX:MaxRAMPercentage`
   (default 25%). Also note the unreachable total (`unreachable`) and whether the dump was
   truncated.
2. **What dominates?** Group `top_dominators` by class and sum `retained`. Look at the top
   classes in `histogram` by retained size. Classes along one chain (cache entry ->
   `DeployedDrg` -> parsed DMN) repeat the same bytes; don't add them up. State the share of
   the reachable heap.
3. **Who owns it?** The owner of each top dominator, and its partition (`engine_states`).
   For the DRG cache: entries per partition against the limit, and each segment's `count`
   against its `maxSegWeight` ([queries.md](queries.md)).
4. **What were the threads doing?** Count threads per stack pattern in the `.threads` file
   ([queries.md](queries.md)). A thread in `OutOfMemoryError.<init>` threw an OOM,
   but its allocation may be unrelated to what fills the heap. Judge the cause by what
   retains the heap. The pod log confirms which OOM killed the broker.
5. **Is it the same on every broker?** One row of numbers per dump. Compare sets of ids
   through their hashes ([privacy.md](privacy.md)).

## Worked example: the DRG cache fills the heap

Ten 8.9.23 brokers with an 8Gi pod limit and no `-Xmx`.

1. **Full.** Reachable heap 1.94-1.96 GiB of a max heap of about 2 GiB (25% of 8Gi). Only
   11-18 MB was unreachable. 4 of the 10 dumps were truncated.
2. **Dominates.** `DeployedDrg` held 92-93% of the reachable heap. On one broker: 1,165
   `DeployedDrg`, 1.7 MB on average, up to 14 MB. The process cache was about 18 MB.
3. **Owner.** On one broker, the top dominators were `DbDecisionState` and its `drgCache`
   (`LocalCache`). Each partition had 350-425 DRG cache entries against a limit of 1000
   (the default of `zeebe.broker.experimental.engine.caches.drgCacheCapacity`, per
   partition), in 4 segments with a cap of 250; none was at its cap. The entries were too
   large for the heap, not too many.
4. **Threads.** 9 of the 10 brokers were replaying DMN deployments: `ReplayStateMachine` ->
   `DecisionRequirementsCreatedApplier` -> DRG cache load -> `DmnScalaDecisionEngine.parse`.
   One broker threw its OOM on an unrelated allocation.
5. **Every broker.** The same 38 DRGs (by hashed id) on every broker, with up to 44 versions
   each.

Conclusion: many versions of the same DMN models, cached on every partition, filled the
heap below the cache limit. Replay refills the cache: for each DRG version,
`DbDecisionState.updateLatestDecisionRequirementsVersion` loads and parses the previous
version through the cache. Levers: a lower `drgCacheCapacity`,
a larger heap, fewer redeploys of unchanged DMN models.
