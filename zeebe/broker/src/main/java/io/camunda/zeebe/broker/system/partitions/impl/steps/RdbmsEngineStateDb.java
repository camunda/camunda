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
import io.camunda.zeebe.engine.state.rdbms.RdbmsEngineStateSchema;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental PoC: builds the mixed RocksDB/RDBMS engine state of a partition, and wipes the
 * partition's rows, since restarts are not supported. The schema is created before the broker
 * starts.
 */
final class RdbmsEngineStateDb {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsEngineStateDb.class);

  private RdbmsEngineStateDb() {}

  /** The returned database owns no connection: relational stores borrow one per transaction. */
  static MixedZeebeDb<ZbColumnFamilies> open(
      final ZeebeDb<ZbColumnFamilies> rocksDb,
      final RdbmsEngineStateResources engineState,
      final int partitionId) {
    LOG.warn(
        "EXPERIMENTAL: partition {} stores element instances in a relational database (mixed "
            + "RocksDB/RDBMS engine state). All rows of this partition are deleted on every start. "
            + "Restarts and replication factor > 1 are NOT supported.",
        partitionId);

    try {
      deletePartitionRows(engineState.dataSource(), partitionId);
    } catch (final SQLException e) {
      throw new IllegalStateException(
          "Failed to prepare the engine state database for partition " + partitionId, e);
    }
    // after the wipe, this creates the row of the position, which every commit updates
    return new MixedZeebeDb<>(rocksDb, engineState.readLastProcessedPosition(partitionId));
  }

  /**
   * Only the engine state tables are wiped: other tables in the same schema, e.g. the RDBMS
   * exporter's, have partition_id columns too.
   */
  private static void deletePartitionRows(final DataSource dataSource, final int partitionId)
      throws SQLException {
    try (final var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      for (final String table : RdbmsEngineStateSchema.TABLES) {
        try (final var delete =
            connection.prepareStatement("DELETE FROM " + table + " WHERE partition_id = ?")) {
          delete.setInt(1, partitionId);
          final int deleted = delete.executeUpdate();
          LOG.info("Deleted {} rows of partition {} from table {}", deleted, partitionId, table);
        }
      }
      connection.commit();
    }
  }
}
