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

  /** Creates a {@link ReplicationLagProvider} appropriate for the configured database. */
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
