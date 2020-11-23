/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions.impl.steps;

import io.zeebe.broker.exporter.stream.ExporterDirector;
import io.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;

public class ExporterDirectorPartitionStep implements PartitionStep {
  private static final int EXPORTER_PROCESSOR_ID = 1003;
  private static final String EXPORTER_NAME = "Exporter-%d";

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final var exporterDescriptors = context.getExporterRepository().getExporters().values();

    final ExporterDirectorContext exporterCtx =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(
                Actor.buildActorName(
                    context.getNodeId(), String.format(EXPORTER_NAME, context.getPartitionId())))
            .logStream(context.getLogStream())
            .zeebeDb(context.getZeebeDb())
            .descriptors(exporterDescriptors);

    final ExporterDirector director = new ExporterDirector(exporterCtx, !context.shouldExport());
    context.setExporterDirector(director);
    return director.startAsync(context.getScheduler());
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    final ActorFuture<Void> future = context.getExporterDirector().closeAsync();
    context.setExporterDirector(null);
    return future;
  }

  @Override
  public String getName() {
    return "ExporterDirector";
  }
}
