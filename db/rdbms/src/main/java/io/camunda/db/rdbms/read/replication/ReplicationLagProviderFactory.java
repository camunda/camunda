/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReplicationLagProviderFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ReplicationLagProviderFactory.class);
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final ReplicationStatusMapper replicationStatusMapper;
  private final ReplicationLsnProviderFactory replicationLsnProviderFactory;

  public ReplicationLagProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper) {
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.replicationStatusMapper = replicationStatusMapper;
    replicationLsnProviderFactory =
        new ReplicationLsnProviderFactory(vendorDatabaseProperties, replicationStatusMapper);
  }

  /**
   * Creates a {@link ReplicationLagProvider}. Azure SQL Database is detected and served natively
   * via {@link AzureGeoReplicationLagProvider} since it has no LSN of its own. Every other database
   * that supports LSN-based monitoring (PostgreSQL, Aurora, on-prem/containerized SQL Server Always
   * On) already reports a DB-measured lag alongside its LSN, so those are served by wrapping their
   * {@link ReplicationLsnProvider} via {@link LsnBackedReplicationLagProvider}. Only a database
   * that supports neither (e.g. plain MySQL without Aurora) fails, with the same {@link
   * IllegalStateException} {@link ReplicationLsnProviderFactory} would throw for it.
   */
  public ReplicationLagProvider create() {
    if (ReplicationLsnProviderFactory.MSSQL_DATABASE_ID.equals(
            vendorDatabaseProperties.databaseId())
        && replicationStatusMapper.isAzureSqlDatabase()) {
      LOG.debug("Detected Azure SQL Geo-Replication LagProvider");
      return new AzureGeoReplicationLagProvider(replicationStatusMapper);
    }
    LOG.debug("Deriving LagProvider from LSN provider");
    return new LsnBackedReplicationLagProvider(replicationLsnProviderFactory.create());
  }
}
