/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.rocksdb;

/**
 * Opt-in tuning for a {@link RocksDbStateStoreProvider}. {@link #DEFAULTS} preserves the provider's
 * stock behavior exactly (consistency checks on, stock compaction); nothing changes for callers
 * that don't pass a tuning. Callers whose state is fully re-derivable from a source log can trade
 * the checks for write throughput and opt into delete-aware compaction to keep tombstone-heavy
 * column families compact.
 *
 * @param consistencyChecks whether ZeebeDb verifies transaction preconditions and foreign keys on
 *     every write. The checks only guard against processor bugs corrupting otherwise authoritative
 *     state; where the store is a disposable projection of a replayable source log, the recovery
 *     path for a corrupt store is to delete it and replay, so the per-write cost buys nothing.
 * @param deleteAwareCompaction whether SST files are periodically recompacted so accumulated
 *     tombstones get purged even in column families that compaction would otherwise never revisit.
 *     ZeebeDb exposes no hook for RocksDB's delete-triggered table-properties collector
 *     (CompactOnDeletionCollector), so this uses the closest reachable equivalent: periodic
 *     compaction, which rewrites — and thereby drops the tombstones from — any SST file older than
 *     {@link RocksDbStateStoreProvider#PERIODIC_COMPACTION_INTERVAL}.
 */
public record StoreTuning(boolean consistencyChecks, boolean deleteAwareCompaction) {

  /** The provider's stock behavior: consistency checks on, stock compaction. */
  public static final StoreTuning DEFAULTS = new StoreTuning(true, false);
}
