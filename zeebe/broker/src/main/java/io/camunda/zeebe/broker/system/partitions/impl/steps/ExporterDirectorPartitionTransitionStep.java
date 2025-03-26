/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExportingCfg;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.impl.SkipPositionsFilter;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExporterDirectorPartitionTransitionStep implements PartitionTransitionStep {

  private static final int EXPORTER_PROCESSOR_ID = 1003;

  private final BiFunction<ExporterDirectorContext, ExporterPhase, ExporterDirector>
      exporterDirectorBuilder;

  public ExporterDirectorPartitionTransitionStep() {
    this(ExporterDirector::new);
  }

  @VisibleForTesting("to allow mocking ExporterDirector in tests")
  ExporterDirectorPartitionTransitionStep(
      final BiFunction<ExporterDirectorContext, ExporterPhase, ExporterDirector>
          exporterDirectorBuilder) {
    this.exporterDirectorBuilder = exporterDirectorBuilder;
  }

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
      context.getComponentHealthMonitor().removeComponent(director);
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
    final var exporterDescriptors = getEnabledExporterDescriptors(context);
    final BrokerCfg brokerCfg = context.getBrokerCfg();
    final ExportingCfg exportingCfg = brokerCfg.getExporting();
    final var exporterFilter = SkipPositionsFilter.of(exportingCfg.skipRecords());
    final ExporterMode exporterMode =
        targetRole == Role.LEADER ? ExporterMode.ACTIVE : ExporterMode.PASSIVE;
    final ExporterDirectorContext exporterCtx =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(Actor.buildActorName("Exporter", context.getPartitionId()))
            .clock(context.getStreamClock())
            .logStream(context.getLogStream())
            .zeebeDb(context.getZeebeDb())
            .distributionInterval(exportingCfg.distributionInterval())
            .partitionMessagingService(context.getMessagingService())
            .descriptors(exporterDescriptors)
            .exporterMode(exporterMode)
            .positionsToSkipFilter(exporterFilter)
            .meterRegistry(context.getPartitionTransitionMeterRegistry());

    final ExporterDirector director =
        exporterDirectorBuilder.apply(exporterCtx, context.getExporterPhase());

    context.getComponentHealthMonitor().registerComponent(director);

    final var startFuture = director.startAsync(context.getActorSchedulingService());
    startFuture.onComplete(
        (nothing, error) -> {
          if (error == null) {
            context.setExporterDirector(director);
            // Pause/Resume here in case the state was changed after the director was created
            switch (context.getExporterPhase()) {
              case PAUSED:
                director.pauseExporting();
                break;
              case SOFT_PAUSED:
                director.softPauseExporting();
                break;
              default:
                director.resumeExporting();
                break;
            }

            // The config might have changed after ExporterDirector has created
            disableOrEnableExportersIfConfigChanged(exporterDescriptors, context);
          }
        });
    return startFuture;
  }

  private void disableOrEnableExportersIfConfigChanged(
      final Map<ExporterDescriptor, ExporterInitializationInfo> startedExporters,
      final PartitionTransitionContext context) {
    final var currentEnabledExporters = getEnabledExporterDescriptors(context);

    for (final var exporter : startedExporters.keySet()) {
      if (!currentEnabledExporters.containsKey(exporter)) {
        context.getExporterDirector().disableExporter(exporter.getId());
      }
    }

    for (final var exporterEntry : currentEnabledExporters.entrySet()) {
      final var exporter = exporterEntry.getKey();
      if (!startedExporters.containsKey(exporter)) {
        context
            .getExporterDirector()
            .enableExporterWithRetry(exporter.getId(), exporterEntry.getValue(), exporter);
      }
    }
  }

  private static Map<ExporterDescriptor, ExporterInitializationInfo> getEnabledExporterDescriptors(
      final PartitionTransitionContext context) {
    final Collection<ExporterDescriptor> exporterDescriptors = context.getExportedDescriptors();
    final var exporterConfig = context.getDynamicPartitionConfig().exporting().exporters();
    return exporterDescriptors.stream()
        .filter(exporterDescriptor -> isEnabled(exporterConfig, exporterDescriptor))
        .collect(
            Collectors.toMap(
                Function.identity(),
                descriptor -> getInitializationInfo(descriptor, exporterConfig)));
  }

  private static ExporterInitializationInfo getInitializationInfo(
      final ExporterDescriptor descriptor, final Map<String, ExporterState> exportersConfig) {
    if (exportersConfig.containsKey(descriptor.getId())) {
      final ExporterState config = exportersConfig.get(descriptor.getId());
      return new ExporterInitializationInfo(
          config.metadataVersion(), config.initializedFrom().orElse(null));
    }

    // TODO: This case won't happen after https://github.com/camunda/camunda/issues/18296 and we
    // handle the default behaviour for newly added exporters.
    return new ExporterInitializationInfo(0, null);
  }

  private static boolean isEnabled(
      final Map<String, ExporterState> exporterConfig,
      final ExporterDescriptor exporterDescriptor) {
    return exporterConfig.containsKey(exporterDescriptor.getId())
        && exporterConfig.get(exporterDescriptor.getId()).state() == State.ENABLED;
  }
}
