/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class ExporterDirectorPartitionTransitionStep implements PartitionTransitionStep {

  private static final int EXPORTER_PROCESSOR_ID = 1003;

  @Override
  public ActorFuture<PartitionTransitionContext> transitionTo(
      final PartitionTransitionContext context,
      final long currentTerm,
      final PartitionRole currentRole,
      final long nextTerm,
      final PartitionRole targetRole) {

    final var exporterDirector = context.getExporterDirector();

    switch (targetRole) {
      case LEADER:
        {
          if (!context.shouldExport()) {
            exporterDirector.pauseExporting();
          } else {
            exporterDirector.resumeExporting();
          }
          break;
        }
      default:
        {
          exporterDirector.pauseExporting();
        }
    }

    return CompletableActorFuture.completed(context);
  }

  @Override
  public String getName() {
    return "ExporterDirector";
  }
}
