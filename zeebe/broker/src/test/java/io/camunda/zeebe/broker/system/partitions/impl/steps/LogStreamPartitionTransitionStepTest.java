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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.time.InstantSource;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

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
    transitionContext.setBrokerCfg(new BrokerCfg());
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));
    transitionContext.setLogStorage(mock(AtomixLogStorage.class));

    when(raftPartition.getServer()).thenReturn(raftServer);
    transitionContext.setRaftPartition(raftPartition);

    doReturn(logStream).when(logStreamBuilder).build();

    step = new LogStreamPartitionTransitionStep(() -> logStreamBuilder);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
  void shouldCloseExistingLogStream(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getLogStream()).isNull();
    verify(logStreamFromPrevRole).close();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldInstallLogStream(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStream = transitionContext.getLogStream();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStream()).isNotNull().isNotEqualTo(existingLogStream);
    verify(logStreamBuilder).build();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
  void shoulNotReInstallLogStorage(final Role currentRole, final Role targetRole) {
    // given
    initializeContext(currentRole);
    final var existingLogStream = transitionContext.getLogStream();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getLogStream()).isEqualTo(existingLogStream);
    verify(logStreamBuilder, never()).build();
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
    verify(logStreamFromPrevRole).close();
    verify(logStreamBuilder, never()).build();
  }

  @Test
  void shouldReturnFailedFutureIfClosingLogStreamThrows() {
    // given
    final var testException = new IllegalStateException("Failed to close");
    initializeContext(Role.LEADER);
    doThrow(testException).when(logStreamFromPrevRole).close();

    // when
    final var future = step.prepareTransition(transitionContext, 2, Role.FOLLOWER);

    // then
    assertThatThrownBy(future::join)
        .isInstanceOf(ExecutionException.class)
        .hasRootCause(testException);

    assertThat(transitionContext.getLogStream()).isSameAs(logStreamFromPrevRole);
  }

  @Test
  void shouldSetStreamClockBeforeBuildingLogStream() {
    // given
    transitionContext.setCurrentRole(Role.INACTIVE);
    final var clockCaptor = ArgumentCaptor.forClass(InstantSource.class);

    // when
    step.transitionTo(transitionContext, 1, Role.LEADER).join();

    // then
    verify(logStreamBuilder).withClock(clockCaptor.capture());
    assertThat(clockCaptor.getValue())
        .as("log stream is built with the installed stream clock")
        .isNotNull()
        .isSameAs(transitionContext.getStreamClock());
  }

  @Test
  void shouldReturnFailedFutureIfLogStreamBuildFails() {
    // given
    final var testException = new IllegalStateException("Segment is already closed");
    transitionContext.setCurrentRole(Role.INACTIVE);
    doThrow(testException).when(logStreamBuilder).build();

    // when
    final var transitionFuture = step.transitionTo(transitionContext, 1, Role.LEADER);

    // then
    assertThatThrownBy(transitionFuture::join)
        .isInstanceOf(ExecutionException.class)
        .rootCause()
        .isSameAs(testException);

    assertThat(transitionContext.getLogStream()).isNull();
    assertThat(transitionContext.getStreamClock()).isNull();
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
