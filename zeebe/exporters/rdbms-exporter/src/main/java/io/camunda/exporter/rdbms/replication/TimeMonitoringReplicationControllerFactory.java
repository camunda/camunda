/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationLagProvider;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import java.time.InstantSource;

public class TimeMonitoringReplicationControllerFactory implements ReplicationControllerFactory {

  private final ReplicationLagProvider statusProvider;
  private final ReplicationConfiguration config;
  private final int partitionId;
  private final InstantSource clock;
  private final RdbmsWriterMetrics metrics;

  public TimeMonitoringReplicationControllerFactory(
      final ReplicationLagProvider statusProvider,
      final ReplicationConfiguration config,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    this.statusProvider = statusProvider;
    this.config = config;
    this.partitionId = partitionId;
    this.clock = clock;
    this.metrics = metrics;
  }

  @Override
  public ReplicationController createReplicationController(final Controller controller) {
    final var strategy = new TimeMonitoringReplicationSignalStrategy(statusProvider, config);
    return new DefaultReplicationController(
        controller, strategy, config, partitionId, clock, metrics);
  }
}
