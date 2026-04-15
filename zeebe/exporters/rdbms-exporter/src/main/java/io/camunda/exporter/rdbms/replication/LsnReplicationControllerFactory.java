/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.write.ReplicationLogStatusProvider;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;

public class LsnReplicationControllerFactory implements ReplicationControllerFactory {

  private final int partitionId;
  private final ReplicationLogStatusProvider replicationLagProvider;
  private final ReplicationConfiguration replicationConfiguration;

  public LsnReplicationControllerFactory(
      final ReplicationLogStatusProvider lsnProvider,
      final ReplicationConfiguration replicationConfiguration,
      final int partitionId) {
    replicationLagProvider = lsnProvider;
    this.replicationConfiguration = replicationConfiguration;
    this.partitionId = partitionId;
  }

  @Override
  public ReplicationController createReplicationController(final Controller controller) {
    return new LsnReplicationController(
        controller, replicationLagProvider, replicationConfiguration, partitionId);
  }
}
