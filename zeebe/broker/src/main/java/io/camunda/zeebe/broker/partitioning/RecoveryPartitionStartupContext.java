/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;

/** Carries per-partition state through the recovery startup and shutdown steps. */
public final class RecoveryPartitionStartupContext {

  private final PartitionId partitionId;
  private final Path partitionDirectory;
  private final ActorSchedulingService schedulingService;
  private final MeterRegistry meterRegistry;
  private final TopologyManagerImpl topologyManager;
  private final ConcurrencyControl concurrencyControl;

  public RecoveryPartitionStartupContext(
      final PartitionId partitionId,
      final Path partitionDirectory,
      final ActorSchedulingService schedulingService,
      final TopologyManagerImpl topologyManager,
      final MeterRegistry meterRegistry,
      final ConcurrencyControl concurrencyControl) {
    this.partitionId = partitionId;
    this.partitionDirectory = partitionDirectory;
    this.schedulingService = schedulingService;
    this.meterRegistry = meterRegistry;
    this.topologyManager = topologyManager;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public String toString() {
    return "RecoveryPartitionStartupContext{partition=" + partitionId + '}';
  }

  public PartitionId partitionId() {
    return partitionId;
  }

  public Path partitionDirectory() {
    return partitionDirectory;
  }

  public ActorSchedulingService schedulingService() {
    return schedulingService;
  }

  public MeterRegistry meterRegistry() {
    return meterRegistry;
  }

  public TopologyManagerImpl topologyManager() {
    return topologyManager;
  }

  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }
}
