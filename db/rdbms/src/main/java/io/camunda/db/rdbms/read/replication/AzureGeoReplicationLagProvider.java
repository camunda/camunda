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
 * Azure SQL geo-replication implementation using {@code sys.dm_geo_replication_link_status}. Azure
 * SQL does not expose a WAL log-sequence number, so this implements {@link ReplicationLagProvider}
 * rather than {@link ReplicationLsnProvider} — the lag-based controller checks the {@code
 * replication_lag_sec} column reported by this view and applies backpressure whenever the lag
 * exceeds a configured threshold.
 *
 * <p>Each row in {@code sys.dm_geo_replication_link_status} represents one geo-replica link. {@code
 * replicationLagMs} is set to {@code replication_lag_sec * 1000}.
 */
public final class AzureGeoReplicationLagProvider implements ReplicationLagProvider {

  private final ReplicationStatusMapper mapper;

  public AzureGeoReplicationLagProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<ReplicationLagStatus> getReplicationStatuses() {
    return mapper.getAzureGeoReplicationStatus();
  }
}
