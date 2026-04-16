/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import java.util.List;

/**
 * Azure SQL implementation. Azure does not expose an LSN equivalent usable for replica progress
 * tracking, so this provider is expected to report per-replica lag (in milliseconds) on each
 * {@link ReplicationStatusDto} via Azure-specific DMVs (e.g. {@code
 * sys.dm_geo_replication_link_status}).
 */
public final class AzureReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  @Override
  public long getCurrent() {
    // Azure does not expose a usable LSN — signal LSN-based checking is not available.
    return -1;
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    // TODO: implement via Azure-specific query (e.g. sys.dm_geo_replication_link_status),
    //  returning one row per replica with replicationLagMs populated.
    return List.of();
  }
}
