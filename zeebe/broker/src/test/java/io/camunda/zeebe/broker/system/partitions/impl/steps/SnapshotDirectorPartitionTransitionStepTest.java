/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.util.health.HealthMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class SnapshotDirectorPartitionTransitionStepTest {

  private static final long LAST_LOG_POSITION = 9;
  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();

  @RegisterExtension
  private final ControlledActorSchedulerExtension schedulerExtension =
      new ControlledActorSchedulerExtension();

  private SnapshotDirectorPartitionTransitionStep step;
  private final RaftPartition raftPartition = mock(RaftPartition.class);
  private final RaftPartitionServer raftServer = mock(RaftPartitionServer.class);
  private final AsyncSnapshotDirector snapshotDirectorFromPrevRole =
      mock(AsyncSnapshotDirector.class);
  private final LogStream logStream = mock(LogStream.class);
  private final LogStreamReader logstreamReader = mock(LogStreamReader.class);

  @BeforeEach
  void setup() {
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));
    transitionContext.setStreamProcessor(mock(StreamProcessor.class));
    transitionContext.setZeebeDb(mock(ZeebeDb.class));
    transitionContext.setBrokerCfg(new BrokerCfg());
    transitionContext.setLogStream(logStream);

    when(raftPartition.getServer()).thenReturn(raftServer);
    transitionContext.setRaftPartition(raftPartition);

    // Use the real ActorScheduler from the extension instead of a mock
    transitionContext.setActorSchedulingService(schedulerExtension.getActorScheduler());

    when(snapshotDirectorFromPrevRole.closeAsync())
        .thenReturn(TestActorFuture.completedFuture(null));

    when(logStream.newLogStreamReader()).thenReturn(logstreamReader);
    when(logstreamReader.seekToEnd()).thenReturn(LAST_LOG_POSITION);

    step = new SnapshotDirectorPartitionTransitionStep();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
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
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
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
    if (Role.LEADER.equals(targetRole)) {
      // verify that the last position is read to notify snapshot director
      verify(logstreamReader, times(1)).seekToEnd();
      final ActorFuture<Long> commitPosition =
          transitionContext.getSnapshotDirector().getCommitPosition();
      schedulerExtension.workUntilDone();
      assertThat(commitPosition.join()).isEqualTo(LAST_LOG_POSITION);
    }
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
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

  private void initializeContext(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setSnapshotDirector(snapshotDirectorFromPrevRole);
    }
  }

  private void transitionTo(final Role role) {
    final ActorFuture<Void> prepareFuture = step.prepareTransition(transitionContext, 1, role);
    schedulerExtension.workUntilDone();
    prepareFuture.join();
    final ActorFuture<Void> transition = step.transitionTo(transitionContext, 1, role);
    schedulerExtension.workUntilDone();
    transition.join();
    transitionContext.setCurrentRole(role);
  }
}
