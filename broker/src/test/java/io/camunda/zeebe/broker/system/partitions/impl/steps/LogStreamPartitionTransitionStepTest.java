/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.future.TestActorFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class LogStreamPartitionTransitionStepTest {
  final TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  private LogStreamPartitionTransitionStep step;
  private final RaftPartition raftPartition = mock(RaftPartition.class);
  private final RaftPartitionServer raftServer = mock(RaftPartitionServer.class);
  private final LogStream logStream = mock(LogStream.class);
  private final LogStreamBuilder logStreamBuilder = spy(LogStream.builder());
  private final LogStream logStreamFromPrevRole = mock(LogStream.class);

  @BeforeEach
  void setup() {
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));
    transitionContext.setLogStorage(mock(AtomixLogStorage.class));

    when(raftPartition.getServer()).thenReturn(raftServer);
    transitionContext.setRaftPartition(raftPartition);

    doReturn(TestActorFuture.completedFuture(logStream)).when(logStreamBuilder).buildAsync();
    when(logStream.closeAsync()).thenReturn(TestActorFuture.completedFuture(null));
    when(logStreamFromPrevRole.closeAsync()).thenReturn(TestActorFuture.completedFuture(null));

    step = new LogStreamPartitionTransitionStep(() -> logStreamBuilder);
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldCloseExistingLogStream")
  void shouldCloseExistingLogStream(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getLogStream()).isNull();
    verify(logStreamFromPrevRole).closeAsync();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldInstallLogStream")
  void shouldInstallLogStream(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStream = transitionContext.getLogStream();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStream()).isNotNull().isNotEqualTo(existingLogStream);
    verify(logStreamBuilder).buildAsync();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shoulNotReInstallLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStream = transitionContext.getLogStream();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStream()).isEqualTo(existingLogStream);
    verify(logStreamBuilder, never()).buildAsync();
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
    assertThat(transitionContext.getStreamProcessor()).isNull();
    verify(logStreamFromPrevRole).closeAsync();
    verify(logStreamBuilder, never()).buildAsync();
  }

  private static Stream<Arguments> provideTransitionsThatShouldCloseExistingLogStream() {
    return Stream.of(
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldInstallLogStream() {
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
      transitionContext.setLogStream(logStreamFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
