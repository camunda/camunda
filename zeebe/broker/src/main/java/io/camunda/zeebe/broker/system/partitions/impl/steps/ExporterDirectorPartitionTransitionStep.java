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
import io.camunda.zeebe.broker.exporter.stream.BlockingExporter;
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
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
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
            .meterRegistry(context.getPartitionTransitionMeterRegistry())
            .engineName(brokerCfg.getExperimental().getDefaultEngineName())
            .sendOnLegacySubject(brokerCfg.getExperimental().isSendOnLegacySubject())
            .receiveOnLegacySubject(brokerCfg.getExperimental().isReceiveOnLegacySubject());

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
            deleteOrEnableExportersIfConfigChanged(exporterDescriptors, context);
          }
        });
    return startFuture;
  }

  private void deleteOrEnableExportersIfConfigChanged(
      final Map<ExporterDescriptor, ExporterInitializationInfo> startedExporters,
      final PartitionTransitionContext context) {
    final var currentEnabledExporters = getEnabledExporterDescriptors(context);

    for (final var exporter : startedExporters.keySet()) {
      if (!currentEnabledExporters.containsKey(exporter)) {
        context.getExporterDirector().removeExporter(exporter.getId());
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

    return exporterConfig.entrySet().stream()
        .filter(
            entry ->
                entry.getValue().state() == State.ENABLED
                    // Exporters whose configuration is not found are considered as enabled, but
                    // since configuration is not found, we cannot export any records to them.
                    || entry.getValue().state() == State.CONFIG_NOT_FOUND)
        .map(entry -> getExporterDescriptor(entry.getKey(), entry.getValue(), exporterDescriptors))
        .collect(Collectors.toMap(Tuple::getLeft, Tuple::getRight));
  }

  private static Tuple<ExporterDescriptor, ExporterInitializationInfo> getExporterDescriptor(
      final String id,
      final ExporterState config,
      final Collection<ExporterDescriptor> exporterDescriptors) {

    return exporterDescriptors.stream()
        .filter(exporterDescriptor -> exporterDescriptor.getId().equals(id))
        .findAny()
        // If a configured exporter is found, return the descriptor and its initialization info.
        .map(
            exporterDescriptor -> {
              final ExporterInitializationInfo initializationInfo =
                  new ExporterInitializationInfo(
                      config.metadataVersion(), config.initializedFrom().orElse(null));
              return Tuple.of(exporterDescriptor, initializationInfo);
            })
        // if the exporter's config is not found, return a blocking exporter descriptor as a
        // placeholder. Exporting to this exporter will be blocked.
        .orElse(
            Tuple.of(
                new ExporterDescriptor(id, BlockingExporter.class, Map.of()),
                new ExporterInitializationInfo(
                    config.metadataVersion(), config.initializedFrom().orElse(null))));
  }
}
