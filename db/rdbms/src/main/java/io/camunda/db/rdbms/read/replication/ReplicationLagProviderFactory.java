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

  private final ReplicationLsnProviderFactory replicationLsnProviderFactory;

  public ReplicationLagProviderFactory(
      final VendorDatabaseProperties vendorDatabaseProperties,
      final ReplicationStatusMapper replicationStatusMapper) {
    replicationLsnProviderFactory =
        new ReplicationLsnProviderFactory(vendorDatabaseProperties, replicationStatusMapper);
  }

  /** Creates a {@link ReplicationLagProvider}. */
  public ReplicationLagProvider create() {
    return new LsnBackedReplicationLagProvider(replicationLsnProviderFactory.create());
  }
}
