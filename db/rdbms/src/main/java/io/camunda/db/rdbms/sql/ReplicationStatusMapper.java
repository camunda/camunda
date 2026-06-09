/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.replication.ReplicationLogStatus;
import java.util.List;

public interface ReplicationStatusMapper {

  long getCurrentLogStatus();

  List<ReplicationLogStatus> getReplicationStatus();

  /**
   * Returns {@code true} when connected to AWS Aurora. For PostgreSQL uses {@code
   * to_regprocedure('aurora_version()')} to check function existence without calling it — safe on
   * any PostgreSQL-compatible database. For MySQL checks {@code information_schema.ROUTINES} for
   * the Aurora-only {@code aurora_version} function. Neither variant throws on non-Aurora
   * databases.
   */
  boolean isAurora();

  /**
   * Returns {@code true} when the instance is part of an Aurora Global Database. For PostgreSQL
   * uses {@code to_regprocedure('aurora_global_db_instance_status()')} — safe on any PostgreSQL.
   * For MySQL checks {@code information_schema.ROUTINES}. Neither variant throws. Only meaningful
   * after {@link #isAurora()} has returned {@code true}.
   */
  boolean isAuroraGlobalDatabase();

  /**
   * Returns the primary's current durable LSN for Aurora Global Database. Queries the {@code
   * aurora_global_db_instance_status()} function and returns the {@code durable_lsn} for the row
   * with {@code session_id = 'MASTER_SESSION_ID'}.
   */
  long getAuroraCurrentLogStatus();

  /**
   * Returns per-replica replication status for Aurora Global Database. Queries the {@code
   * aurora_global_db_instance_status()} function and returns one row per non-primary instance with
   * its {@code durable_lsn} (as {@code logStatus}), {@code server_id} (as {@code replicaId}), and
   * {@code visibility_lag_in_msec} (as {@code replicationLagMs}).
   */
  List<ReplicationLogStatus> getAuroraReplicationStatus();
}
