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

/** Reports replication lag for Azure SQL Database geo-replication. */
public final class AzureGeoReplicationLagProvider implements ReplicationLagProvider {

  private final ReplicationStatusMapper mapper;

  public AzureGeoReplicationLagProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrentDbTime() {
    return mapper.getAzureCurrentDbTime();
  }

  @Override
  public List<ReplicationLagStatus> getReplicationStatuses() {
    return mapper.getAzureGeoReplicationStatus();
  }
}
