/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class ExporterDirectorPartitionBootstrapStep implements PartitionBootstrapStep {

  private static final int EXPORTER_PROCESSOR_ID = 1003;

  @Override
  public ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context) {
    final var exporterDescriptors = context.getExporterRepository().getExporters().values();

    final ExporterDirectorContext exporterCtx =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(Actor.buildActorName(context.getNodeId(), "Exporter", context.getPartitionId()))
            .logStream(context.getLogStream())
            .zeebeDb(context.getZeebeDb())
            .descriptors(exporterDescriptors);

    final ExporterDirector director = new ExporterDirector(exporterCtx, true);

    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    final var startFuture = director.startAsync(context.getActorSchedulingService());
    startFuture.onComplete(
        (nothing, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            context.setExporterDirector(director);
            context.getComponentHealthMonitor().registerComponent(director.getName(), director);

            result.complete(context);
          }
        });
    return result;
  }

  @Override
  public ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context) {
    final var director = context.getExporterDirector();

    final ActorFuture<Void> future = director.closeAsync();
    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    future.onComplete(
        (nothing, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            context.getComponentHealthMonitor().removeComponent(director.getName());
            context.setExporterDirector(null);

            result.complete(context);
          }
        });

    return result;
  }

  @Override
  public String getName() {
    return "ExporterDirector";
  }
}
