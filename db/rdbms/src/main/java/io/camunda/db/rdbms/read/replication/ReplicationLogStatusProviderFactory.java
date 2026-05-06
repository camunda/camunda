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
import java.util.Optional;

public final class ReplicationLogStatusProviderFactory {

  public static final String POSTGRESQL_DATABASE_ID = "postgresql";

  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final ReplicationStatusMapper replicationStatusMapper;

  public ReplicationLogStatusProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper) {
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.replicationStatusMapper = replicationStatusMapper;
  }

  public Optional<ReplicationLogStatusProvider> create() {
    return switch (vendorDatabaseProperties.databaseId()) {
      case POSTGRESQL_DATABASE_ID ->
          Optional.of(new PostgresReplicationLogStatusProvider(replicationStatusMapper));
      case null, default -> Optional.empty();
    };
  }
}
