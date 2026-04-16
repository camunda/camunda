/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import java.util.List;

/**
 * PostgreSQL implementation using {@code pg_current_wal_lsn()} and {@code pg_stat_replication}.
 *
 * <p>Per-replica lag is read from {@code pg_stat_replication.replay_lag} — the elapsed time
 * between flushing WAL locally and the standby acknowledging it has been replayed (applied).
 * Disconnected replicas simply do not appear in the view and therefore do not contribute a row;
 * the caller is expected to combine the row count with a configured {@code minSyncReplicas}
 * quorum to detect broken replication.
 *
 * <p>Note: the {@code replay_lag} column is restricted to superusers by default. The exporter user
 * needs the {@code pg_monitor} or {@code pg_read_all_stats} role to observe it; otherwise the lag
 * reads as 0.
 */
public final class PostgresReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  private final ExporterPositionMapper mapper;

  public PostgresReplicationLogStatusProvider(final ExporterPositionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.findCurrentLsnPostgres();
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    return mapper.getReplicationStatusesPostgres();
  }
}
