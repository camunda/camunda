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

public final class ReplicationLogStatusProviderFactory {

  public static final String POSTGRESQL_DATABASE_ID = "postgresql";
  public static final String MSSQL_DATABASE_ID = "mssql";

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

  /**
   * Aurora Global Database presents itself as PostgreSQL over JDBC. When the {@code
   * aurora_global_db_instance_status} function is found in {@code pg_proc} we use the
   * Aurora-specific provider that reads {@code durable_lsn}; otherwise we fall back to the standard
   * PostgreSQL provider that reads {@code pg_stat_replication}.
   */
  private ReplicationLogStatusProvider createPostgresOrAuroraProvider() {
    if (replicationStatusMapper.isAurora()) {
      return new AuroraReplicationLogStatusProvider(replicationStatusMapper);
    }
    return new DefaultReplicationLogStatusProvider(replicationStatusMapper);
  }
}
