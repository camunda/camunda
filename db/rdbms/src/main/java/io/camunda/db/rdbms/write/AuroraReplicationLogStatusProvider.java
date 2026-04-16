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
 * Aurora (MySQL/PostgreSQL) implementation using {@code aurora_global_db_instance_status()} to
 * track durable LSN across global database instances.
 *
 * <p>Per-replica lag is read from the {@code visibility_lag_in_msec} column of that view — the
 * time in milliseconds between a change being durable on the primary and visible on a given
 * secondary. If the cluster is not a global cluster the view returns no non-primary rows; the
 * caller is expected to combine the row count with a configured {@code minSyncReplicas} quorum to
 * detect broken replication.
 */
public final class AuroraReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  private final ExporterPositionMapper mapper;

  public AuroraReplicationLogStatusProvider(final ExporterPositionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.findCurrentLsnAurora();
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    return mapper.getReplicationStatusesAurora();
  }
}
