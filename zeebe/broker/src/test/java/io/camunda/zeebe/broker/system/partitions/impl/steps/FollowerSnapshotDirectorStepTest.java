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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.FollowerSnapshotDirector;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitorable;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

final class FollowerSnapshotDirectorStepTest {

  @RegisterExtension
  private final ActorSchedulerExtension scheduler = new ActorSchedulerExtension();

  private final TestPartitionTransitionContext context = new TestPartitionTransitionContext();
  private final HealthMonitor healthMonitor = mock(HealthMonitor.class);
  private final FollowerSnapshotDirectorStep step =
      new FollowerSnapshotDirectorStep(Optional::empty, mock(ConstructableSnapshotStore.class));

  @BeforeEach
  void setUp() {
    final var raftPartition = mock(RaftPartition.class);
    when(raftPartition.getServer()).thenReturn(mock(RaftPartitionServer.class));
    context.setRaftPartition(raftPartition);
    context.setComponentHealthMonitor(healthMonitor);
    context.setBrokerCfg(new BrokerCfg());
    context.setActorSchedulingService(scheduler.get());
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"LEADER", "INACTIVE"})
  void shouldStartDirectorWhenBecomingFollower(final Role currentRole) {
    // given
    transitionTo(currentRole);

    // when
    transitionTo(Role.FOLLOWER);

    // then
    final var director = registeredDirector();
    assertThat(director.isActorClosed()).isFalse();
  }

  @Test
  void shouldNotStartDirectorOnLeader() {
    // when
    transitionTo(Role.LEADER);

    // then
    verify(healthMonitor, never()).registerComponent(any());
  }

  @Test
  void shouldKeepDirectorAcrossFollowerTerms() {
    // given
    transitionTo(Role.FOLLOWER);
    final var director = registeredDirector();

    // when
    transitionTo(Role.FOLLOWER);

    // then
    assertThat(registeredDirector()).isSameAs(director);
    assertThat(director.isActorClosed()).isFalse();
    verify(healthMonitor, never()).removeComponent(any(HealthMonitorable.class));
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"LEADER", "INACTIVE"})
  void shouldStopDirectorWhenNoLongerFollower(final Role targetRole) {
    // given
    transitionTo(Role.FOLLOWER);
    final var director = registeredDirector();

    // when
    transitionTo(targetRole);

    // then
    assertThat(director.isActorClosed()).isTrue();
    verify(healthMonitor).removeComponent(director);
  }

  @Test
  void shouldStartNewDirectorWhenBecomingFollowerAgain() {
    // given
    transitionTo(Role.FOLLOWER);
    final var previous = registeredDirector();
    transitionTo(Role.LEADER);

    // when
    transitionTo(Role.FOLLOWER);

    // then
    final var captor = ArgumentCaptor.forClass(FollowerSnapshotDirector.class);
    verify(healthMonitor, times(2)).registerComponent(captor.capture());
    final var current = captor.getAllValues().getLast();
    assertThat(current).isNotSameAs(previous);
    assertThat(current.isActorClosed()).isFalse();
  }

  private FollowerSnapshotDirector registeredDirector() {
    final var captor = ArgumentCaptor.forClass(FollowerSnapshotDirector.class);
    verify(healthMonitor).registerComponent(captor.capture());
    return captor.getValue();
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(context, 1, role).join();
    step.transitionTo(context, 1, role).join();
    context.setCurrentRole(role);
  }
}
