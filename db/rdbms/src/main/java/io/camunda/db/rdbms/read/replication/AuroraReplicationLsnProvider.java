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
 * AWS Aurora Global Database implementation using {@code aurora_global_db_instance_status()}: the
 * primary is the row with {@code session_id = 'MASTER_SESSION_ID'}; each secondary region reports
 * its own {@code durable_lsn} and {@code visibility_lag_in_msec}.
 */
public final class AuroraReplicationLsnProvider implements ReplicationLsnProvider {

  private final ReplicationStatusMapper mapper;

  public AuroraReplicationLsnProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.getAuroraCurrentLogStatus();
  }

  @Override
  public long getCurrentDbTime() {
    return mapper.getCurrentDbTime();
  }

  @Override
  public List<ReplicationLsnStatus> getReplicationStatuses() {
    return mapper.getAuroraReplicationStatus();
  }
}
