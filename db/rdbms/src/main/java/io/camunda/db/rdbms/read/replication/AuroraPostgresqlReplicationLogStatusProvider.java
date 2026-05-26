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
 * Aurora PostgreSQL implementation using {@code aurora_global_db_instance_status()} to track a
 * durable LSN across global database instances.
 *
 * <p>Aurora MySQL exposes different replication metadata and will require its own provider.
 *
 * <p>Per-replica lag is read from {@code visibility_lag_in_msec} — the time in milliseconds between
 * a change being durable on the writer and visible on a given secondary. If the cluster is not a
 * global cluster the function returns no non-primary rows; the caller is expected to combine the
 * row count with a configured {@code minSyncReplicas} quorum to detect broken replication.
 */
public final class AuroraPostgresqlReplicationLogStatusProvider
    implements ReplicationLogStatusProvider {

  private final ReplicationStatusMapper mapper;

  public AuroraPostgresqlReplicationLogStatusProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.getCurrentAuroraPostgresqlLogStatus();
  }

  @Override
  public List<ReplicationLogStatus> getReplicationStatuses() {
    return mapper.getAuroraPostgresqlReplicationStatus();
  }
}
