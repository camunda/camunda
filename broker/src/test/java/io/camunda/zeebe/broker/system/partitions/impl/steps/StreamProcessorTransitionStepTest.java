/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorBuilder;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.future.TestActorFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class StreamProcessorTransitionStepTest {

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();
  final StreamProcessorBuilder streamProcessorBuilder = spy(StreamProcessorBuilder.class);
  final StreamProcessor streamProcessor = mock(StreamProcessor.class);
  final StreamProcessor streamProcessorFromPrevRole = mock(StreamProcessor.class);

  private StreamProcessorTransitionStep step;

  @BeforeEach
  void setup() {
    transitionContext.setLogStream(mock(LogStream.class));
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));

    doReturn(streamProcessor).when(streamProcessorBuilder).build();

    when(streamProcessor.openAsync(anyBoolean())).thenReturn(TestActorFuture.completedFuture(null));
    when(streamProcessor.closeAsync()).thenReturn(TestActorFuture.completedFuture(null));
    when(streamProcessorFromPrevRole.closeAsync())
        .thenReturn(TestActorFuture.completedFuture(null));

    step = new StreamProcessorTransitionStep((ctx, role) -> streamProcessor);
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldCloseExistingStreamProcessor")
  void shouldCloseExistingStreamProcessor(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setStreamProcessor(streamProcessorFromPrevRole);
    }

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getStreamProcessor()).isNull();
    verify(streamProcessorFromPrevRole).closeAsync();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldReInstallStreamProcessor")
  void shouldReInstallStreamProcessor(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setStreamProcessor(streamProcessorFromPrevRole);
    }

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getStreamProcessor())
        .isNotNull()
        .isNotEqualTo(streamProcessorFromPrevRole);
    verify(streamProcessor).openAsync(anyBoolean());
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotCloseExistingStreamProcessor(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setStreamProcessor(streamProcessorFromPrevRole);
    }
    final var existingStreamProcessor = transitionContext.getStreamProcessor();

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getStreamProcessor()).isEqualTo(existingStreamProcessor);
    verify(streamProcessorFromPrevRole, never()).closeAsync();
  }

  @ParameterizedTest
  @MethodSource("provideTransitionsThatShouldDoNothing")
  void shouldNotReInstallStreamProcessor(final Role currentRole, final Role targetRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setStreamProcessor(streamProcessorFromPrevRole);
    }
    final var existingStreamProcessor = transitionContext.getStreamProcessor();
    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getStreamProcessor()).isEqualTo(existingStreamProcessor);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"FOLLOWER", "LEADER", "CANDIDATE"})
  void shouldCloseStreamProcessorWhenTransitioningToInactive(final Role currentRole) {
    // given
    transitionContext.setCurrentRole(currentRole);
    transitionContext.setStreamProcessor(streamProcessorFromPrevRole);

    // when
    transitionTo(Role.INACTIVE);

    // then
    assertThat(transitionContext.getStreamProcessor()).isNull();
    verify(streamProcessorFromPrevRole).closeAsync();
  }

  private static Stream<Arguments> provideTransitionsThatShouldCloseExistingStreamProcessor() {
    return Stream.of(
        Arguments.of(Role.FOLLOWER, Role.LEADER),
        Arguments.of(Role.CANDIDATE, Role.LEADER),
        Arguments.of(Role.LEADER, Role.FOLLOWER),
        Arguments.of(Role.LEADER, Role.INACTIVE),
        Arguments.of(Role.FOLLOWER, Role.INACTIVE),
        Arguments.of(Role.CANDIDATE, Role.INACTIVE));
  }

  private static Stream<Arguments> provideTransitionsThatShouldReInstallStreamProcessor() {
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

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
