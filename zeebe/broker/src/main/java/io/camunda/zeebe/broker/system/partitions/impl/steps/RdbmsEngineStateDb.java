/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.jdbc.MixedZeebeDb;
import io.camunda.zeebe.engine.state.rdbms.RdbmsEngineStateResources;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental PoC: builds the mixed RocksDB/RDBMS engine state of a partition. The schema is
 * created before the broker starts.
 *
 * <p>The database keeps its rows across restarts. It stores the position of the last command whose
 * changes it contains, so that replaying the log after a restart rebuilds RocksDB but doesn't apply
 * these changes to the database again.
 *
 * <p>Known limitations: while the replay skips the database, event appliers read the database rows
 * at that position instead of at the replayed event.
 *
 * <ul>
 *   <li>An applier that expects a row which was removed later fails, and so does the replay: e.g.
 *       replaying the activation of a job that was completed before the restart, if the last
 *       snapshot doesn't cover the activation.
 *   <li>RocksDB state that appliers derive from database rows may diverge. This concerns flows
 *       whose state is only partly in the database (e.g. timers, messages, incidents); in a flow of
 *       service tasks, RocksDB only holds the key generator and the position, which are set from
 *       the replayed records themselves.
 * </ul>
 */
final class RdbmsEngineStateDb {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsEngineStateDb.class);

  private RdbmsEngineStateDb() {}

  /**
   * The returned database owns no connection: relational stores borrow one per transaction.
   *
   * @throws IllegalStateException if the partition is replicated: every replica's stream processor
   *     would write into the one shared database
   */
  static MixedZeebeDb<ZbColumnFamilies> open(
      final ZeebeDb<ZbColumnFamilies> rocksDb,
      final RdbmsEngineStateResources engineState,
      final int partitionId,
      final int replicationFactor) {
    if (replicationFactor > 1) {
      throw new IllegalStateException(
          ("Expected replication factor 1 for the experimental RDBMS engine state, but was %d: "
                  + "followers replay the log continuously and would write into the database "
                  + "shared with the leader")
              .formatted(replicationFactor));
    }

    final long lastProcessedPosition = engineState.readLastProcessedPosition(partitionId);
    LOG.warn(
        "EXPERIMENTAL: partition {} stores element instances in a relational database (mixed "
            + "RocksDB/RDBMS engine state). The database keeps its rows across restarts and "
            + "contains the changes up to position {}, which the replay doesn't apply again; the "
            + "replay may fail on rows removed after the last snapshot. Replication factor > 1 is "
            + "NOT supported.",
        partitionId,
        lastProcessedPosition);
    return new MixedZeebeDb<>(
        rocksDb, lastProcessedPosition, engineState.openPartition(partitionId));
  }
}
