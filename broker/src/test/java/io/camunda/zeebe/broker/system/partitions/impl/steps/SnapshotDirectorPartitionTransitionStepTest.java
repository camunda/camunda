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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.future.TestActorFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SnapshotDirectorPartitionTransitionStepTest {

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  private SnapshotDirectorPartitionTransitionStep step;
  private final RaftPartition raftPartition = mock(RaftPartition.class);
  private final RaftPartitionServer raftServer = mock(RaftPartitionServer.class);
  private final ActorSchedulingService actorSchedulingService = mock(ActorSchedulingService.class);
  private final AsyncSnapshotDirector snapshotDirectorFromPrevRole =
      mock(AsyncSnapshotDirector.class);

  @BeforeEach
  void setup() {
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));
    transitionContext.setStreamProcessor(mock(StreamProcessor.class));
    transitionContext.setBrokerCfg(new BrokerCfg());

    when(raftPartition.getServer()).thenReturn(raftServer);
    transitionContext.setRaftPartition(raftPartition);

    when(actorSchedulingService.submitActor(any()))
        .thenReturn(TestActorFuture.completedFuture(null));
    transitionContext.setActorSchedulingService(actorSchedulingService);

    when(snapshotDirectorFromPrevRole.closeAsync())
        .thenReturn(TestActorFuture.completedFuture(null));

    step = new SnapshotDirectorPartitionTransitionStep();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldCloseExistingSnapshotDirector")
  void shouldCloseExistingSnapshotDirector(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getSnapshotDirector()).isNull();
    verify(snapshotDirectorFromPrevRole).closeAsync();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldInstallSnapshotDirector")
  void shouldReInstallSnapshotDirector(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingSnapshotDirector = transitionContext.getSnapshotDirector();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getSnapshotDirector())
        .isNotNull()
        .isNotEqualTo(existingSnapshotDirector);
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotReInstallSnapshotDirector(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingSnapshotDirector = transitionContext.getSnapshotDirector();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getSnapshotDirector()).isEqualTo(existingSnapshotDirector);
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
    assertThat(transitionContext.getSnapshotDirector()).isNull();
  }

  private static Stream<Arguments> provideTransitionsThatShouldCloseExistingSnapshotDirector() {
    return Stream.of(
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldInstallSnapshotDirector() {
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
      transitionContext.setSnapshotDirector(snapshotDirectorFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
