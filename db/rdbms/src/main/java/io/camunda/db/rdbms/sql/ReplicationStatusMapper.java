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
   * Returns {@code true} when connected to AWS Aurora (PostgreSQL or MySQL). For PostgreSQL the
   * detection relies on the presence of the {@code aurora_global_db_instance_status} function in
   * {@code pg_proc}. For MySQL the detection checks for the Aurora-only {@code aurora_version}
   * system variable in {@code performance_schema.global_variables}. Neither check invokes the
   * Aurora function itself, so both are safe to run on any compatible database.
   */
  boolean isAurora();

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
