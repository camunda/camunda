/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import java.util.List;

/**
 * MSSQL implementation using {@code sys.dm_hadr_database_replica_states} (Always On AG).
 *
 * <p>The primary's current position is read from {@code end_of_log_lsn} on the local replica row
 * ({@code is_local=1}). Per-replica lag is derived from {@code redo_lsn} (how far the replica has
 * applied the log) and {@code last_redone_time} (approximate wall-clock lag). Replicas that are
 * disconnected or not yet seeded will not appear in the view.
 */
public final class MssqlReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  private final ReplicationStatusMapper mapper;

  public MssqlReplicationLogStatusProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.getCurrentLogStatus();
  }

  @Override
  public List<ReplicationLogStatus> getReplicationStatuses() {
    return mapper.getReplicationStatus();
  }
}
