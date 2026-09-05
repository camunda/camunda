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

public final class ReplicationLsnProviderFactory {

  public static final String POSTGRESQL_DATABASE_ID = "postgresql";
  public static final String MSSQL_DATABASE_ID = "mssql";
  public static final String MYSQL_DATABASE_ID = "mysql";
  private static final Logger LOG = LoggerFactory.getLogger(ReplicationLsnProviderFactory.class);
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final ReplicationStatusMapper replicationStatusMapper;

  public ReplicationLsnProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper) {
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.replicationStatusMapper = replicationStatusMapper;
  }

  public ReplicationLsnProvider create() {
    return switch (vendorDatabaseProperties.databaseId()) {
      case POSTGRESQL_DATABASE_ID -> createPostgresOrAuroraProvider();
      case MYSQL_DATABASE_ID -> createMysqlAuroraProvider();
      case MSSQL_DATABASE_ID -> createMssqlProvider();
      case null ->
          throw new IllegalArgumentException(
              "Cannot create ReplicationLsnProvider for null database id");
      default ->
          throw new IllegalArgumentException(
              "Cannot create ReplicationLsnProvider for unknown database id "
                  + vendorDatabaseProperties.databaseId());
    };
  }

  private ReplicationLsnProvider createPostgresOrAuroraProvider() {
    if (!replicationStatusMapper.isAurora()) {
      validateRequiredPrivileges("'pg_monitor' role");
      LOG.debug("Detected PostgreSQL LogStatusProvider");
      return new DefaultReplicationLsnProvider(replicationStatusMapper);
    }
    return createAuroraGlobalProvider();
  }

  private ReplicationLsnProvider createMssqlProvider() {
    if (replicationStatusMapper.isAzureSqlDatabase()) {
      throw new IllegalStateException(
          "LSN-based replication monitoring (asyncReplication.type=LOG_SEQ) is not supported on "
              + "Azure SQL Database because it does not expose a log sequence number. Use "
              + "asyncReplication.type=TIME_LAG instead.");
    }
    validateRequiredPrivileges("'VIEW SERVER STATE' or 'VIEW SERVER PERFORMANCE STATE' permission");
    LOG.debug("Detected MSSQL LogStatusProvider");
    return new DefaultReplicationLsnProvider(replicationStatusMapper);
  }

  private void validateRequiredPrivileges(final String requiredPrivilege) {
    if (!replicationStatusMapper.hasRequiredPrivileges()) {
      throw new IllegalStateException(
          "The database user does not have the "
              + requiredPrivilege
              + " required for LSN-based async replication monitoring.");
    }
  }

  private ReplicationLsnProvider createMysqlAuroraProvider() {
    if (!replicationStatusMapper.isAurora()) {
      throw new IllegalStateException(
          "Replication monitoring requires AWS Aurora MySQL. "
              + "Plain MySQL does not support the LSN-based replication monitoring API.");
    }
    return createAuroraGlobalProvider();
  }

  private ReplicationLsnProvider createAuroraGlobalProvider() {
    if (!replicationStatusMapper.isAuroraGlobalDatabase()) {
      throw new IllegalStateException(
          "Replication monitoring requires AWS Aurora Global Database. "
              + "Aurora is detected but Global Database is not configured on this instance.");
    }
    LOG.debug("Detected Aurora Global LogStatusProvider");
    return new AuroraReplicationLsnProvider(replicationStatusMapper);
  }
}
