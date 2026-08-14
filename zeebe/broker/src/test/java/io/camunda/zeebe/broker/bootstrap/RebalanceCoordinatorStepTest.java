/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.rebalance.RebalanceCoordinator;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RebalanceCoordinatorStepTest {

  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  private final TestConcurrencyControl spyConcurrencyControl = spy(new TestConcurrencyControl());
  private final ClusterConfigurationService clusterConfigurationService =
      mock(ClusterConfigurationService.class);

  private MockBrokerStartupContext testBrokerStartupContext;
  private ClusterCommunicationService communicationService;

  private final RebalanceCoordinatorStep sut = new RebalanceCoordinatorStep();

  @BeforeEach
  void setUp() {
    actorSchedulerRule.before();

    testBrokerStartupContext = new MockBrokerStartupContext();
    testBrokerStartupContext.setConcurrencyControl(spyConcurrencyControl);
    testBrokerStartupContext.setActorSchedulingService(actorSchedulerRule.get());
    testBrokerStartupContext.setClusterConfigurationService(clusterConfigurationService);

    communicationService = testBrokerStartupContext.getClusterServices().getCommunicationService();
  }

  @AfterEach
  void tearDown() {
    actorSchedulerRule.after();
  }

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Rebalance Coordinator");
  }

  @Nested
  class StartupBehavior {

    @Test
    void shouldWaitForActorSubmission() {
      // given
      final var actorSchedulingService = mock(ActorSchedulingService.class);
      final ActorFuture<Void> submitActorFuture = new CompletableActorFuture<>();
      when(actorSchedulingService.submitActor(any())).thenReturn(submitActorFuture);
      testBrokerStartupContext.setActorSchedulingService(actorSchedulingService);

      // when
      final var startupFuture = sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture.isDone()).isFalse();
      verify(clusterConfigurationService, never()).addUpdateListener(any());

      // when
      submitActorFuture.complete(null);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(any(RebalanceCoordinator.class));
    }

    @Test
    void shouldFailStartupWhenActorSubmissionFails() {
      // given
      final var actorSchedulingService = mock(ActorSchedulingService.class);
      final var expectedError = new RuntimeException("expected failure");
      final ActorFuture<Void> submitActorFuture = new CompletableActorFuture<>();
      submitActorFuture.completeExceptionally(expectedError);
      when(actorSchedulingService.submitActor(any())).thenReturn(submitActorFuture);
      testBrokerStartupContext.setActorSchedulingService(actorSchedulingService);

      // when
      final var startupFuture = sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture).failsWithin(TIME_OUT);
      verify(clusterConfigurationService, never()).addUpdateListener(any());
      verifyNoInteractions(communicationService);
    }

    @Test
    void shouldRegisterHandlersAndListenerOnSuccessfulStartup() {
      // when
      final var startupFuture = sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(any(RebalanceCoordinator.class));
      verify(communicationService, timeout(TIME_OUT.toMillis()).times(3))
          .replyTo(any(), any(), any(), any());
    }
  }

  @Nested
  class ShutdownBehavior {

    @Test
    void shouldBeSafeAfterPartialStartup() {
      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      verifyNoInteractions(clusterConfigurationService);
      verifyNoInteractions(communicationService);
    }

    @Test
    void shouldCloseHandlersRemoveListenerAbandonCoordinatorAndCloseActor() {
      // given
      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);

      final ArgumentCaptor<RebalanceCoordinator> coordinatorCaptor =
          ArgumentCaptor.forClass(RebalanceCoordinator.class);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(coordinatorCaptor.capture());
      final var registeredCoordinator = coordinatorCaptor.getValue();

      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      verify(communicationService, times(3)).unsubscribe(any());
      verify(clusterConfigurationService).removeUpdateListener(registeredCoordinator);
    }

    @Test
    void shouldCompleteWithoutWaitingForAbandonedRunner() {
      // given
      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);

      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(Duration.ofSeconds(2));
    }

    @Test
    void shouldBeSafeToShutdownTwice() {
      // given
      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      assertThat(sut.shutdown(testBrokerStartupContext)).succeedsWithin(TIME_OUT);

      // when
      final var secondShutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(secondShutdownFuture).succeedsWithin(TIME_OUT);
    }
  }
}
