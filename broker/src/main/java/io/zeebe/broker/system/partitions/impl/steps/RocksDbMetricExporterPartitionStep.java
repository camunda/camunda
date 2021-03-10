/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl.steps;

import static io.zeebe.engine.state.DefaultZeebeDbFactory.DEFAULT_DB_METRIC_EXPORTER_FACTORY;

import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;

public class RocksDbMetricExporterPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final var metricExporter =
        DEFAULT_DB_METRIC_EXPORTER_FACTORY.apply(
            Integer.toString(context.getPartitionId()), context.getZeebeDb());
    final var metricsTimer =
        context
            .getActor()
            .runAtFixedRate(
                Duration.ofSeconds(5),
                () -> {
                  if (context.getZeebeDb() != null) {
                    metricExporter.exportMetrics();
                  }
                });

    context.setMetricsTimer(metricsTimer);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    context.getMetricsTimer().cancel();
    context.setMetricsTimer(null);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "RocksDB metric timer";
  }
}
