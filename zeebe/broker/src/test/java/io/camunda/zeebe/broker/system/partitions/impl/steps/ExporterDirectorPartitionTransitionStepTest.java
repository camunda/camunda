/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

    when(actorSchedulingService.submitActor(any(), any()))
        .thenReturn(TestActorFuture.completedFuture(null));
    transitionContext.setActorSchedulingService(actorSchedulingService);

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
