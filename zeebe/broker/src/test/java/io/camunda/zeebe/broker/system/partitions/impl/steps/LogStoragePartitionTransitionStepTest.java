/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStoragePartitionTransitionStep.NotLeaderException;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class LogStoragePartitionTransitionStepTest {

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  private LogStoragePartitionTransitionStep step;
  private final RaftPartition raftPartition = mock(RaftPartition.class);
  private final RaftPartitionServer raftServer = mock(RaftPartitionServer.class);
  private final AtomixLogStorage logStorageFromPrevRole = mock(AtomixLogStorage.class);

  @BeforeEach
  void setup() {
    transitionContext.setLogStream(mock(LogStream.class));
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));

    when(raftPartition.getServer()).thenReturn(raftServer);
    when(raftServer.getTerm()).thenReturn(1L);
    when(raftServer.getAppender()).thenReturn(Optional.of(mock(ZeebeLogAppender.class)));
    transitionContext.setRaftPartition(raftPartition);

    step = new LogStoragePartitionTransitionStep();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"INACTIVE", "FOLLOWER", "CANDIDATE"})
  void shouldThrowRecoverableExceptionOnTermMismatch(final Role currentRole) {
    // given
    initializeContext(currentRole);
    step.prepareTransition(transitionContext, 1, Role.LEADER);

    // simulate term change in Raft
    when(raftServer.getTerm()).thenReturn(2L);

    // when + then
    assertThatThrownBy(() -> step.transitionTo(transitionContext, 1, Role.LEADER).join())
        .hasCauseInstanceOf(NotLeaderException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"INACTIVE", "FOLLOWER", "CANDIDATE"})
  void shouldThrowRecoverableExceptionWhenNoAppender(final Role currentRole) {
    // given
    initializeContext(currentRole);
    step.prepareTransition(transitionContext, 1, Role.LEADER);

    // simulate term change in Raft
    when(raftServer.getAppender()).thenReturn(Optional.empty());

    // when + then
    assertThatThrownBy(() -> step.transitionTo(transitionContext, 1, Role.LEADER).join())
        .hasCauseInstanceOf(NotLeaderException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
  void shouldCloseExistingLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getLogStorage()).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldInstallLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStorage = transitionContext.getLogStorage();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStorage()).isNotNull().isNotEqualTo(existingLogStorage);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
  void shouldNotReInstallLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStorage = transitionContext.getLogStorage();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStorage()).isEqualTo(existingLogStorage);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "LEADER", "CANDIDATE"})
  void shouldCloseWhenTransitioningToInactive(final Role currentRole) {
    // given
    initializeContext(currentRole);

    // when
    transitionTo(Role.INACTIVE);

    // then
    assertThat(transitionContext.getLogStorage()).isNull();
  }

  private void initializeContext(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setLogStorage(logStorageFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
