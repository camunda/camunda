/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProvider;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import java.time.InstantSource;

public class LsnReplicationControllerFactory implements ReplicationControllerFactory {

  private final int partitionId;
  private final ReplicationLogStatusProvider replicationLagProvider;
  private final ReplicationConfiguration replicationConfiguration;
  private final InstantSource clock;
  private final RdbmsWriterMetrics metrics;

  public LsnReplicationControllerFactory(
      final ReplicationLogStatusProvider lsnProvider,
      final ReplicationConfiguration replicationConfiguration,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    replicationLagProvider = lsnProvider;
    this.replicationConfiguration = replicationConfiguration;
    this.partitionId = partitionId;
    this.clock = clock;
    this.metrics = metrics;
  }

  @Override
  public ReplicationController createReplicationController(final Controller controller) {
    return new LsnReplicationController(
        controller, replicationLagProvider, replicationConfiguration, partitionId, clock, metrics);
  }
}
