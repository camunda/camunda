/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Collection;

public final class ExporterDirectorPartitionTransitionStep implements PartitionTransitionStep {

  private static final int EXPORTER_PROCESSOR_ID = 1003;

  @Override
  public void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {
    final var director = context.getExporterDirector();
    if (director != null && shouldCloseOnTransition(newRole, context.getCurrentRole())) {
      director.pauseExporting();
    }
  }

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var director = context.getExporterDirector();
    if (director != null && shouldCloseOnTransition(targetRole, context.getCurrentRole())) {
      context.getComponentHealthMonitor().removeComponent(director.getName());
      final ActorFuture<Void> future = director.closeAsync();
      future.onComplete(
          (success, error) -> {
            if (error == null) {
              context.setExporterDirector(null);
            }
          });

      return future;
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {

    if (shouldInstallOnTransition(targetRole, context.getCurrentRole())
        || (context.getExporterDirector() == null && targetRole != Role.INACTIVE)) {
      return openExporter(context, targetRole);
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public String getName() {
    return "ExporterDirector";
  }

  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }

  private boolean shouldCloseOnTransition(final Role newRole, final Role currentRole) {
    return shouldInstallOnTransition(newRole, currentRole) || newRole == Role.INACTIVE;
  }

  private ActorFuture<Void> openExporter(
      final PartitionTransitionContext context, final Role targetRole) {
    final Collection<ExporterDescriptor> exporterDescriptors = context.getExportedDescriptors();

    final ExporterMode exporterMode =
        targetRole == Role.LEADER ? ExporterMode.ACTIVE : ExporterMode.PASSIVE;
    final ExporterDirectorContext exporterCtx =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(Actor.buildActorName(context.getNodeId(), "Exporter", context.getPartitionId()))
            .logStream(context.getLogStream())
            .zeebeDb(context.getZeebeDb())
            .partitionMessagingService(context.getMessagingService())
            .descriptors(exporterDescriptors)
            .exporterMode(exporterMode);

    final ExporterDirector director = new ExporterDirector(exporterCtx, !context.shouldExport());

    context.getComponentHealthMonitor().registerComponent(director.getName(), director);

    final var startFuture = director.startAsync(context.getActorSchedulingService());
    startFuture.onComplete(
        (nothing, error) -> {
          if (error == null) {
            context.setExporterDirector(director);
            // Pause/Resume here in case the state was changed after the director was created
            if (!context.shouldExport()) {
              director.pauseExporting();
            } else {
              director.resumeExporting();
            }
          }
        });
    return startFuture;
  }
}
