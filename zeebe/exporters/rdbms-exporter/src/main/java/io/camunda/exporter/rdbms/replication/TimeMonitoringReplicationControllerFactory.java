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

public class TimeMonitoringReplicationControllerFactory implements ReplicationControllerFactory {

  private final ReplicationLagProvider statusProvider;
  private final ReplicationConfiguration config;
  private final int partitionId;
  private final RdbmsWriterMetrics metrics;

  public TimeMonitoringReplicationControllerFactory(
      final ReplicationLagProvider statusProvider,
      final ReplicationConfiguration config,
      final int partitionId,
      final RdbmsWriterMetrics metrics) {
    this.statusProvider = statusProvider;
    this.config = config;
    this.partitionId = partitionId;
    this.metrics = metrics;
  }

  @Override
  public ReplicationController createReplicationController(final Controller controller) {
    return new TimeMonitoringReplicationController(
        controller, statusProvider, config, partitionId, metrics);
  }
}
