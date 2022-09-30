/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupStep;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDBMetricExporter;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;

public class RockDbMetricExporterPartitionStartupStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "RocksDB metric timer";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(
      final PartitionStartupContext partitionStartupContext) {
    final var metricExporter =
        new ZeebeRocksDBMetricExporter<>(
            Integer.toString(partitionStartupContext.getPartitionId()),
            partitionStartupContext::getZeebeDb);
    final var metricsTimer =
        partitionStartupContext
            .getActorControl()
            .runAtFixedRate(
                Duration.ofSeconds(5),
                () -> {
                  if (partitionStartupContext.getZeebeDb() != null) {
                    metricExporter.exportMetrics();
                  }
                });

    partitionStartupContext.setMetricsTimer(metricsTimer);

    final var statisticsTimer =
        partitionStartupContext
            .getActorControl()
            .runAtFixedRate(
                Duration.ofSeconds(30),
                () -> {
                  if (partitionStartupContext.getZeebeDb() != null) {
                    metricExporter.exportStatistics();
                  }
                });

    partitionStartupContext.setStatisticsTimer(statisticsTimer);

    return CompletableActorFuture.completed(partitionStartupContext);
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(
      final PartitionStartupContext partitionStartupContext) {
    partitionStartupContext.getMetricsTimer().cancel();
    partitionStartupContext.setMetricsTimer(null);
    return CompletableActorFuture.completed(partitionStartupContext);
  }
}
