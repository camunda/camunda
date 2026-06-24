/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.util.health.HealthMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class StreamProcessorTransitionStepTest {

  private static final TestConcurrencyControl TEST_CONCURRENCY_CONTROL =
      new TestConcurrencyControl();

  TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();
  final StreamProcessorBuilder streamProcessorBuilder = spy(StreamProcessorBuilder.class);
  final StreamProcessor streamProcessor = mock(StreamProcessor.class);
  final StreamProcessor streamProcessorFromPrevRole = mock(StreamProcessor.class);

  private StreamProcessorTransitionStep step;

  @BeforeEach
  void setup() {
    transitionContext.setLogStream(mock(LogStream.class));
    transitionContext.setComponentHealthMonitor(mock(HealthMonitor.class));
    transitionContext.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    doReturn(streamProcessor).when(streamProcessorBuilder).build();

    when(streamProcessor.openAsync(anyBoolean())).thenReturn(TestActorFuture.completedFuture(null));
    when(streamProcessor.closeAsync()).thenReturn(TestActorFuture.completedFuture(null));
    when(streamProcessorFromPrevRole.closeAsync())
        .thenReturn(TestActorFuture.completedFuture(null));

    step = new StreamProcessorTransitionStep((ctx, role) -> streamProcessor);
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
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
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
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
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
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
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
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

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }
}
