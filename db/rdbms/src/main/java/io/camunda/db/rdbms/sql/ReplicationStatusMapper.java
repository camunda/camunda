/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.db.rdbms.read.replication.ReplicationLsnStatus;
import java.util.List;

public interface ReplicationStatusMapper {

  long getCurrentLogStatus();

  List<ReplicationLsnStatus> getReplicationStatus();

  /** Returns {@code true} when connected to AWS Aurora. */
  boolean isAurora();

  /**
   * Returns {@code true} when the instance is part of an Aurora Global Database. Only meaningful
   * after {@link #isAurora()} has returned {@code true}.
   */
  boolean isAuroraGlobalDatabase();

  /** Returns the primary's current durable LSN for Aurora Global Database. */
  long getAuroraCurrentLogStatus();

  /** Returns per-replica replication status for Aurora Global Database. */
  List<ReplicationLsnStatus> getAuroraReplicationStatus();

  /**
   * Returns per-replica geo-replication status for Azure SQL, using {@code
   * sys.dm_geo_replication_link_status}. Each row corresponds to one geo-replica link. The {@code
   * replicationLagMs} field is derived from {@code replication_lag_sec * 1000}.
   */
  List<ReplicationLagStatus> getAzureGeoReplicationStatus();

  /**
   * Returns {@code true} when the connected MSSQL instance is genuinely Azure SQL Database, as
   * opposed to on-prem/containerized SQL Server, Azure SQL Managed Instance, or Azure SQL Edge.
   */
  boolean isAzureSqlDatabase();
}
