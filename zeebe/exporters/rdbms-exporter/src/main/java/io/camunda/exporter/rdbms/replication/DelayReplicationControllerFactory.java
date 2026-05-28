/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import java.time.InstantSource;

public class DelayReplicationControllerFactory implements ReplicationControllerFactory {

  private final ReplicationConfiguration config;
  private final int partitionId;
  private final InstantSource clock;

  public DelayReplicationControllerFactory(
      final ReplicationConfiguration config, final int partitionId, final InstantSource clock) {
    this.config = config;
    this.partitionId = partitionId;
    this.clock = clock;
  }

  @Override
  public ReplicationController createReplicationController(final Controller controller) {
    return new DelayReplicationController(controller, config.getDelay(), clock, partitionId);
  }
}
