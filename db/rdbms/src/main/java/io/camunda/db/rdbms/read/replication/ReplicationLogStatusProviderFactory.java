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

  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final ReplicationStatusMapper replicationStatusMapper;
  private final AuroraDatabaseDetector auroraDatabaseDetector;

  private volatile ReplicationLogStatusProvider provider;

  public ReplicationLogStatusProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper,
      final AuroraDatabaseDetector auroraDatabaseDetector) {
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.replicationStatusMapper = replicationStatusMapper;
    this.auroraDatabaseDetector = auroraDatabaseDetector;
  }

  public ReplicationLogStatusProvider create() {
    final var existingProvider = provider;
    if (existingProvider != null) {
      return existingProvider;
    }

    synchronized (this) {
      if (provider == null) {
        provider = createProvider();
      }
      return provider;
    }
  }

  private ReplicationLogStatusProvider createProvider() {
    return switch (vendorDatabaseProperties.databaseId()) {
      case POSTGRESQL_DATABASE_ID -> createPostgresqlProvider();
      case null ->
          throw new IllegalArgumentException(
              "Cannot create ReplicationLogStatusProvider for null database id");
      default ->
          throw new IllegalArgumentException(
              "Cannot create ReplicationLogStatusProvider for unknown database id "
                  + vendorDatabaseProperties.databaseId());
    };
  }

  private ReplicationLogStatusProvider createPostgresqlProvider() {
    if (auroraDatabaseDetector.isAurora()) {
      return new AuroraPostgresqlReplicationLogStatusProvider(replicationStatusMapper);
    }

    return new PostgresReplicationLogStatusProvider(replicationStatusMapper);
  }
}
