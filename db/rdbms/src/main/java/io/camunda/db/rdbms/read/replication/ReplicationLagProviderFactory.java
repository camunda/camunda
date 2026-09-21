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

public final class ReplicationLagProviderFactory {

  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final ReplicationLsnProviderFactory replicationLsnProviderFactory;

  public ReplicationLagProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper) {
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    replicationLsnProviderFactory =
        new ReplicationLsnProviderFactory(vendorDatabaseProperties, replicationStatusMapper);
  }

  /** Creates a {@link ReplicationLagProvider}. */
  public ReplicationLagProvider create() {
    if (ReplicationLsnProviderFactory.ORACLE_DATABASE_ID.equals(
        vendorDatabaseProperties.databaseId())) {
      throw new IllegalStateException(
          "Time-lag based replication monitoring is not supported for Oracle; use SCN-based "
              + "replication monitoring instead.");
    }
    return new LsnBackedReplicationLagProvider(replicationLsnProviderFactory.create());
  }
}
