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

public final class ReplicationLogStatusProviderFactory {

  public static final String POSTGRESQL_DATABASE_ID = "postgresql";
  public static final String MSSQL_DATABASE_ID = "mssql";
  public static final String MYSQL_DATABASE_ID = "mysql";
  private static final Logger LOG =
      LoggerFactory.getLogger(ReplicationLogStatusProviderFactory.class);
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final ReplicationStatusMapper replicationStatusMapper;

  public ReplicationLogStatusProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper) {
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.replicationStatusMapper = replicationStatusMapper;
  }

  public ReplicationLogStatusProvider create() {
    return switch (vendorDatabaseProperties.databaseId()) {
      case POSTGRESQL_DATABASE_ID -> createPostgresOrAuroraProvider();
      case MYSQL_DATABASE_ID -> createMysqlAuroraProvider();
      case MSSQL_DATABASE_ID -> new DefaultReplicationLogStatusProvider(replicationStatusMapper);
      case null ->
          throw new IllegalArgumentException(
              "Cannot create ReplicationLogStatusProvider for null database id");
      default ->
          throw new IllegalArgumentException(
              "Cannot create ReplicationLogStatusProvider for unknown database id "
                  + vendorDatabaseProperties.databaseId());
    };
  }

  private ReplicationLogStatusProvider createPostgresOrAuroraProvider() {
    if (!replicationStatusMapper.isAurora()) {
      LOG.debug("Detected PostgreSQL LogStatusProvider");
      return new DefaultReplicationLogStatusProvider(replicationStatusMapper);
    }
    return createAuroraGlobalProvider();
  }

  private ReplicationLogStatusProvider createMysqlAuroraProvider() {
    if (!replicationStatusMapper.isAurora()) {
      throw new IllegalStateException(
          "Replication monitoring requires AWS Aurora MySQL. "
              + "Plain MySQL does not support the LSN-based replication monitoring API.");
    }
    return createAuroraGlobalProvider();
  }

  private ReplicationLogStatusProvider createAuroraGlobalProvider() {
    if (!replicationStatusMapper.isAuroraGlobalDatabase()) {
      throw new IllegalStateException(
          "Replication monitoring requires AWS Aurora Global Database. "
              + "Aurora is detected but Global Database is not configured on this instance.");
    }
    LOG.debug("Detected Aurora Global LogStatusProvider");
    return new AuroraReplicationLogStatusProvider(replicationStatusMapper);
  }
}
