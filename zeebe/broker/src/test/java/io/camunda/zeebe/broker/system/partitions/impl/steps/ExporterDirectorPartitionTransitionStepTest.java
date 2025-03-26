/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class ExporterDirectorPartitionTransitionStepTest {

  final TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  final ActorSchedulingService actorSchedulingService = mock(ActorSchedulingService.class);
  final ExporterRepository exporterRepository = mock(ExporterRepository.class);
  final ExporterDirector exporterDirectorFromPrevRole = mock(ExporterDirector.class);
  private ExporterDirectorPartitionTransitionStep step;

  @BeforeEach
  void setup() {
    transitionContext.setLogStream(mock(LogStream.class));
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));

    when(exporterRepository.getExporters()).thenReturn(Map.of());
    transitionContext.setExporterRepository(exporterRepository);
    transitionContext.setDynamicPartitionConfig(DynamicPartitionConfig.init());

    when(actorSchedulingService.submitActor(any(), any()))
        .thenReturn(TestActorFuture.completedFuture(null));
    transitionContext.setActorSchedulingService(actorSchedulingService);
    transitionContext.setBrokerCfg(new BrokerCfg());

    when(exporterDirectorFromPrevRole.closeAsync())
        .thenReturn(TestActorFuture.completedFuture(null));

    step = new ExporterDirectorPartitionTransitionStep();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
  void shouldCloseExistingStreamProcessor(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getExporterDirector()).isNull();
    verify(exporterDirectorFromPrevRole).closeAsync();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldInstallExporterDirector(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getExporterDirector())
        .isNotNull()
        .isNotEqualTo(exporterDirectorFromPrevRole);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
  void shouldNotInstallExporterDirector(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingExporterDirector = transitionContext.getExporterDirector();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getExporterDirector()).isEqualTo(existingExporterDirector);
    verify(exporterDirectorFromPrevRole, never()).closeAsync();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "LEADER", "CANDIDATE"})
  void shouldCloseWhenTransitionToInactive(final Role currentRole) {
    // given
    initializeContext(currentRole);

    // when
    transitionTo(Role.INACTIVE);

    // then
    assertThat(transitionContext.getExporterDirector()).isNull();
    verify(exporterDirectorFromPrevRole).closeAsync();
  }

  @Test
  void shouldUseLatestConfigWhenInstallingExporterDirector() {
    // given
    final String enabledExporterId = "expA";
    final String disabledExporterId = "expB";
    final var exporterConfig =
        getExporterConfig(enabledExporterId, State.ENABLED, disabledExporterId, State.DISABLED);

    setExportersInContext(enabledExporterId, disabledExporterId, exporterConfig);

    final AtomicReference<ExporterDirectorContext> capturedContext = new AtomicReference<>();
    final var exporterDirectorStep = getExporterDirectorPartitionTransitionStep(capturedContext);

    // when
    exporterDirectorStep.prepareTransition(transitionContext, 1, Role.LEADER).join();
    exporterDirectorStep.transitionTo(transitionContext, 1, Role.LEADER).join();

    // then
    assertThat(
            capturedContext.get().getDescriptors().keySet().stream().map(ExporterDescriptor::getId))
        .containsExactly(enabledExporterId);
  }

  @Test
  void shouldDisableExporterIfConfigChangedConcurrently() {
    // given
    final String enabledExporterId = "expA";
    final String disabledExporterId = "expB";
    final var exporterConfig =
        getExporterConfig(enabledExporterId, State.ENABLED, disabledExporterId, State.ENABLED);

    setExportersInContext(enabledExporterId, disabledExporterId, exporterConfig);

    final var mockedExporterDirector = mock(ExporterDirector.class);
    final var startingFuture = new TestActorFuture<Void>();
    when(mockedExporterDirector.startAsync(any())).thenReturn(startingFuture);
    final var exporterDirectorStep =
        new ExporterDirectorPartitionTransitionStep((ctx, phase) -> mockedExporterDirector);

    // when
    exporterDirectorStep.prepareTransition(transitionContext, 1, Role.LEADER).join();
    exporterDirectorStep.transitionTo(transitionContext, 1, Role.LEADER);

    final var updatedConfig =
        getExporterConfig(enabledExporterId, State.ENABLED, disabledExporterId, State.DISABLED);
    transitionContext.setDynamicPartitionConfig(updatedConfig);
    startingFuture.complete(null);

    // then
    verify(mockedExporterDirector, timeout(1000)).disableExporter(disabledExporterId);
    verify(mockedExporterDirector, never()).disableExporter(enabledExporterId);
  }

  @Test
  void shouldEnableExporterIfConfigChangedConcurrently() {
    // given
    final String enabledExporterId = "expA";
    final String reEnabledExporterId = "expB";
    final var exporterConfig =
        getExporterConfig(enabledExporterId, State.ENABLED, reEnabledExporterId, State.DISABLED);

    setExportersInContext(enabledExporterId, reEnabledExporterId, exporterConfig);

    final var mockedExporterDirector = mock(ExporterDirector.class);
    final var startingFuture = new TestActorFuture<Void>();
    when(mockedExporterDirector.startAsync(any())).thenReturn(startingFuture);
    final var exporterDirectorStep =
        new ExporterDirectorPartitionTransitionStep((ctx, phase) -> mockedExporterDirector);

    // when
    exporterDirectorStep.prepareTransition(transitionContext, 1, Role.LEADER).join();
    exporterDirectorStep.transitionTo(transitionContext, 1, Role.LEADER);

    final var updatedConfig =
        getExporterConfig(enabledExporterId, State.ENABLED, reEnabledExporterId, State.ENABLED);
    transitionContext.setDynamicPartitionConfig(updatedConfig);
    startingFuture.complete(null);

    // then
    verify(mockedExporterDirector, timeout(1000))
        .enableExporterWithRetry(eq(reEnabledExporterId), any(), any());
  }

  private void setExportersInContext(
      final String enabledExporterId,
      final String disabledExporterId,
      final DynamicPartitionConfig exporterConfig) {
    final Map<String, ExporterDescriptor> exporters =
        Map.of(
            enabledExporterId,
            new ExporterDescriptor(enabledExporterId),
            disabledExporterId,
            new ExporterDescriptor(disabledExporterId));
    when(exporterRepository.getExporters()).thenReturn(exporters);
    transitionContext.setDynamicPartitionConfig(exporterConfig);
  }

  private ExporterDirectorPartitionTransitionStep getExporterDirectorPartitionTransitionStep(
      final AtomicReference<ExporterDirectorContext> capturedContext) {
    final BiFunction<ExporterDirectorContext, ExporterPhase, ExporterDirector> exporterBuilder =
        (context, phase) -> {
          capturedContext.set(context);
          final var mockedExporterDirector = mock(ExporterDirector.class);
          when(mockedExporterDirector.startAsync(any()))
              .thenReturn(TestActorFuture.completedFuture(null));
          return mockedExporterDirector;
        };

    final var exporterDirectorStep = new ExporterDirectorPartitionTransitionStep(exporterBuilder);
    return exporterDirectorStep;
  }

  private DynamicPartitionConfig getExporterConfig(
      final String exporterOne,
      final State exporterOneState,
      final String exporterTwo,
      final State exporterTwoState) {
    return new DynamicPartitionConfig(
        new ExportersConfig(
            Map.of(
                exporterOne,
                new ExporterState(0, exporterOneState, Optional.empty()),
                exporterTwo,
                new ExporterState(0, exporterTwoState, Optional.empty()))));
  }

  private void initializeContext(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setExporterDirector(exporterDirectorFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
