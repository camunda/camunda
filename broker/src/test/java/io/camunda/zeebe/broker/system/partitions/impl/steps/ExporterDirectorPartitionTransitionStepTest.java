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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.future.TestActorFuture;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    when(actorSchedulingService.submitActor(any(), anyInt()))
        .thenReturn(TestActorFuture.completedFuture(null));
    transitionContext.setActorSchedulingService(actorSchedulingService);

    when(exporterDirectorFromPrevRole.closeAsync())
        .thenReturn(TestActorFuture.completedFuture(null));

    step = new ExporterDirectorPartitionTransitionStep();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldCloseExistingExporter")
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
  @MethodSource("provideTransitionsThatShouldInstallExporter")
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
  @MethodSource("provideTransitionsThatShouldDoNothing")
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

  private static Stream<Arguments> provideTransitionsThatShouldCloseExistingExporter() {
    return Stream.of(
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.CANDIDATE),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldInstallExporter() {
    return Stream.of(
        Arguments.of(null, Role.FOLLOWER),
        Arguments.of(null, Role.LEADER),
        Arguments.of(null, Role.CANDIDATE),
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.CANDIDATE),
        Arguments.of(Role.INACTIVE, Role.FOLLOWER),
        Arguments.of(Role.INACTIVE, Role.LEADER),
        Arguments.of(Role.INACTIVE, Role.CANDIDATE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldDoNothing() {
    return Stream.of(
        Arguments.of(Role.CANDIDATE, Role.FOLLOWER),
        Arguments.of(Role.FOLLOWER, Role.CANDIDATE),
        Arguments.of(null, Role.INACTIVE));
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
