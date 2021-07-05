/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static io.camunda.zeebe.engine.state.DefaultZeebeDbFactory.DEFAULT_DB_METRIC_EXPORTER_FACTORY;

import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;

public class RocksDbMetricExporterPartitionBootstrapStep implements PartitionBootstrapStep {

  @Override
  public ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context) {
    final var metricExporter =
        DEFAULT_DB_METRIC_EXPORTER_FACTORY.apply(
            Integer.toString(context.getPartitionId()), context.getZeebeDb());
    final var metricsTimer =
        context
            .getActorControl()
            .runAtFixedRate(
                Duration.ofSeconds(5),
                () -> {
                  if (context.getZeebeDb() != null) {
                    metricExporter.exportMetrics();
                  }
                });

    context.setMetricsTimer(metricsTimer);
    return CompletableActorFuture.completed(context);
  }

  @Override
  public ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context) {
    context.getMetricsTimer().cancel();
    context.setMetricsTimer(null);
    return CompletableActorFuture.completed(context);
  }

  @Override
  public String getName() {
    return "RocksDB metric timer";
  }
}
