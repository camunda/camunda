/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.broker.compaction.PassResult.Outcome;
import io.camunda.zeebe.scheduler.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the compaction {@link CompactionPass} for one compacted data partition on an actor, a
 * sibling of {@code LogRetentionCompactor} in both structure and its replica-local determinism
 * argument: the pass runs on <b>every</b> replica with no cross-replica coordination, and because
 * it is a deterministic function of the replicated committed log (plus the committed manifest,
 * itself derived from that log), every replica converges to byte-identical clean sets. There is no
 * leader, no control record, no compaction protocol.
 *
 * <p>Each tick runs one pass; if the pass committed but the key-offset map overflowed, further
 * passes are run immediately (bounded) to finish the range, since each overflow pass advances the
 * cleaner point and therefore terminates.
 *
 * <h3>Tombstone grace runs on the log clock — determinism is unconditional</h3>
 *
 * <p>The pass takes no wall-clock input at all: tombstone grace is measured against the manifest's
 * {@code maxLogTimestamp} — the running maximum of broker-assigned record timestamps up to the
 * cleaner point — compared with each tombstone's own record timestamp (see {@link CompactionPass}).
 * Both are log-derived, so replicas with the same log and pass lineage make identical drop
 * decisions and hold byte-identical clean sets and manifests. The scheduling interval is the only
 * time-based input this actor has, and it never influences the pass's output — only when it runs.
 *
 * <p>Consequence: an idle partition freezes the log clock, so tombstones at the grace boundary are
 * retained until traffic resumes — the safe failure direction (a rebuilding reader keeps seeing the
 * delete).
 *
 * <p>Threading: all pass work runs on this actor's thread; injected seams are only touched here
 * (the reader-lease counts the trash queue consults are independently thread-safe).
 */
public final class LogCleaner extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(LogCleaner.class);
  private static final int MAX_PASSES_PER_TICK = 64;

  private final int partitionId;
  private final CompactionConfig config;
  private final CompactionPass pass;

  /**
   * @param partitionId the data partition this cleaner serves (for the actor name and logging)
   * @param config the cleaner tunables (the pass interval is taken from here)
   * @param pass the deterministic pass this actor drives
   */
  public LogCleaner(
      final int partitionId, final CompactionConfig config, final CompactionPass pass) {
    this.partitionId = partitionId;
    this.config = config;
    this.pass = pass;
  }

  @Override
  public String getName() {
    return "EventBridgeLogCleaner-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(config.passInterval(), this::runPasses);
  }

  private void runPasses() {
    try {
      int passes = 0;
      PassResult result = pass.runOnce();
      while (result.outcome() == Outcome.COMMITTED
          && result.overflowed()
          && ++passes < MAX_PASSES_PER_TICK) {
        result = pass.runOnce();
      }
      if (result.committed()) {
        LOG.debug(
            "Partition {} — compaction committed cleaner point {}",
            partitionId,
            result.cleanerPoint());
      }
    } catch (final Exception e) {
      // A failed pass leaves the previously committed manifest authoritative; the next tick reruns.
      LOG.warn("Partition {} — compaction pass failed, will retry next interval", partitionId, e);
    }
  }
}
