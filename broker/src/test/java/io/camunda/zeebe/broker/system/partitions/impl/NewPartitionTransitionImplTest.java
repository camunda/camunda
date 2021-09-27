/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NewPartitionTransitionImplTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(NewPartitionTransitionImplTest.class);

  private static final TestConcurrencyControl TEST_CONCURRENCY_CONTROL =
      new TestConcurrencyControl();
  private static final long DEFAULT_TERM = 1L;
  private static final Role DEFAULT_ROLE = Role.LEADER;

  private PartitionTransitionStep mockStep1;
  private PartitionTransitionStep mockStep2;

  private PartitionTransitionContext mockContext;

  @BeforeEach
  void setUp() {
    mockStep1 = mock(PartitionTransitionStep.class);
    mockStep2 = mock(PartitionTransitionStep.class);

    when(mockStep1.getName()).thenReturn("Step 1");
    when(mockStep2.getName()).thenReturn("Step 2");

    mockContext = mock(PartitionTransitionContext.class);
  }

  @Test
  void shouldCallTransitionStepsInOrder() {
    // given
    when(mockStep1.transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));
    when(mockStep2.transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var sut = new NewPartitionTransitionImpl(of(mockStep1, mockStep2), mockContext);
    sut.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    // when
    sut.transitionTo(DEFAULT_TERM, DEFAULT_ROLE).join();

    // then
    final var invocationRecorder = inOrder(mockStep1, mockStep2);
    invocationRecorder.verify(mockStep1).onNewRaftRole(mockContext, DEFAULT_ROLE);
    invocationRecorder.verify(mockStep2).onNewRaftRole(mockContext, DEFAULT_ROLE);
    invocationRecorder.verify(mockStep1).transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE);
    invocationRecorder.verify(mockStep2).transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE);
  }

  @Test
  void shouldAbortTransitionIfOneStepThrowsAnException() {
    // given
    final var testException = new Exception("TEST_EXCEPTION");
    when(mockStep1.transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE))
        .thenReturn(TEST_CONCURRENCY_CONTROL.failedFuture(testException));
    when(mockStep2.transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var sut = new NewPartitionTransitionImpl(of(mockStep1, mockStep2), mockContext);
    sut.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    // when
    final var actualResult = sut.transitionTo(DEFAULT_TERM, DEFAULT_ROLE);

    // then
    verify(mockStep2, never()).transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE);

    assertThatThrownBy(actualResult::join)
        .isInstanceOf(CompletionException.class)
        .getCause()
        .isSameAs(testException);
  }

  @Test
  void shouldAbortOngoingTransitionWhenNewTransitionIsRequested() {
    // given
    final var step1CountdownLatch = new CountDownLatch(1);
    final var step1 = new WaitingTransitionStep(TEST_CONCURRENCY_CONTROL, step1CountdownLatch);
    final var spyStep1 = spy(step1);

    when(mockStep2.transitionTo(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var sut = new NewPartitionTransitionImpl(of(spyStep1, mockStep2), mockContext);
    sut.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    final var secondTerm = 2L;
    final var secondRole = Role.FOLLOWER;

    // when
    final var firstTransitionFuture = sut.transitionTo(DEFAULT_TERM, DEFAULT_ROLE);
    final var secondTransitionFuture = sut.transitionTo(secondTerm, secondRole);

    step1CountdownLatch.countDown();
    await().until(firstTransitionFuture::isDone);
    await().until(secondTransitionFuture::isDone);

    // then

    // both transitions completed orderly
    assertThat(firstTransitionFuture.isCompletedExceptionally()).isFalse();
    assertThat(secondTransitionFuture.isCompletedExceptionally()).isFalse();

    // the first transition was cancelled before the second step
    verify(mockStep2, never()).transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE);
    verify(mockStep2, never()).prepareTransition(mockContext, secondTerm, secondRole);

    final var invocationRecorder = inOrder(spyStep1, mockStep2);
    // first transition sequence
    invocationRecorder.verify(spyStep1).onNewRaftRole(mockContext, DEFAULT_ROLE);
    invocationRecorder.verify(mockStep2).onNewRaftRole(mockContext, DEFAULT_ROLE);
    invocationRecorder.verify(spyStep1).transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE);

    // second transition sequence
    invocationRecorder.verify(spyStep1).onNewRaftRole(mockContext, secondRole);
    invocationRecorder.verify(mockStep2).onNewRaftRole(mockContext, secondRole);
    invocationRecorder.verify(spyStep1).prepareTransition(mockContext, secondTerm, secondRole);
    invocationRecorder.verify(spyStep1).transitionTo(mockContext, secondTerm, secondRole);
    invocationRecorder.verify(mockStep2).transitionTo(mockContext, secondTerm, secondRole);
  }

  @Test
  // regression test for https://github.com/camunda-cloud/zeebe/issues/7873
  void shouldNotStartMultipleTransitions() {
    // given
    final var firstStepFirstTransitionFuture = TEST_CONCURRENCY_CONTROL.<Void>createFuture();
    final var firstStepSecondTransitionFuture = TEST_CONCURRENCY_CONTROL.<Void>createFuture();
    final var firstStepThirdTransitionFuture = TEST_CONCURRENCY_CONTROL.<Void>createFuture();
    when(mockStep1.transitionTo(any(), anyLong(), any()))
        .thenReturn(firstStepFirstTransitionFuture)
        .thenReturn(firstStepSecondTransitionFuture)
        .thenReturn(firstStepThirdTransitionFuture);
    when(mockStep1.prepareTransition(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var transition = new NewPartitionTransitionImpl(of(mockStep1), mockContext);
    transition.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    final var firstTransitionFuture = transition.transitionTo(1, Role.FOLLOWER);

    // when
    transition.transitionTo(2, Role.LEADER);
    transition.transitionTo(2, Role.FOLLOWER);

    firstStepFirstTransitionFuture.complete(null);
    await().until(firstTransitionFuture::isDone);

    //
    // then
    //
    final var inOrder = inOrder(mockStep1);

    // first transition sequence
    inOrder.verify(mockStep1).onNewRaftRole(mockContext, Role.FOLLOWER);
    inOrder.verify(mockStep1).transitionTo(mockContext, 1, Role.FOLLOWER);

    // notify for concurrent transitions
    inOrder.verify(mockStep1).onNewRaftRole(mockContext, Role.LEADER);
    inOrder.verify(mockStep1).onNewRaftRole(mockContext, Role.FOLLOWER);

    // prepare for transition - close resources
    inOrder.verify(mockStep1).prepareTransition(mockContext, 2L, RaftServer.Role.LEADER);

    // skip transition
    inOrder.verify(mockStep1, never()).transitionTo(mockContext, 2, Role.LEADER);

    // transition to third transition, since other is canceled
    inOrder.verify(mockStep1).transitionTo(mockContext, 2, Role.FOLLOWER);

    // when reaching wait state (future) nothing more should happen
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  // regression test for https://github.com/camunda-cloud/zeebe/issues/7873
  void shouldExecuteTransitionsInOrder() {
    // given
    final var firstStepFirstTransitionFuture = TEST_CONCURRENCY_CONTROL.<Void>createFuture();
    final var firstStepSecondTransitionFuture = TEST_CONCURRENCY_CONTROL.<Void>createFuture();
    final var firstStepThirdTransitionFuture = TEST_CONCURRENCY_CONTROL.<Void>createFuture();
    when(mockStep1.transitionTo(any(), anyLong(), any()))
        .thenReturn(firstStepFirstTransitionFuture)
        .thenReturn(firstStepSecondTransitionFuture)
        .thenReturn(firstStepThirdTransitionFuture);
    when(mockStep1.prepareTransition(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var transition = new NewPartitionTransitionImpl(of(mockStep1), mockContext);
    transition.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    transition.transitionTo(1, Role.FOLLOWER);
    transition.transitionTo(2, Role.LEADER);
    final var lastTransitionFuture = transition.transitionTo(2, Role.FOLLOWER);

    // when
    firstStepFirstTransitionFuture.complete(null);
    firstStepSecondTransitionFuture.complete(null);
    firstStepThirdTransitionFuture.complete(null);

    // then
    await().until(lastTransitionFuture::isDone);
    final var inOrder = inOrder(mockStep1);

    // first transition sequence
    inOrder.verify(mockStep1).onNewRaftRole(mockContext, Role.FOLLOWER);
    inOrder.verify(mockStep1).transitionTo(mockContext, 1, Role.FOLLOWER);

    inOrder.verify(mockStep1).onNewRaftRole(mockContext, Role.LEADER);
    inOrder.verify(mockStep1).onNewRaftRole(mockContext, Role.FOLLOWER);

    // second transition
    // Leader transition canceled
    inOrder.verify(mockStep1).prepareTransition(mockContext, 2L, Role.LEADER);
    inOrder.verify(mockStep1, never()).transitionTo(mockContext, 2, Role.LEADER);

    // third transition
    inOrder.verify(mockStep1).transitionTo(mockContext, 2, Role.FOLLOWER);

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldCallTransitionStepsInReverseOrderDuringPreparationForTransitionPhase() {
    // given
    when(mockStep1.transitionTo(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));
    when(mockStep1.prepareTransition(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    when(mockStep2.transitionTo(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));
    when(mockStep2.prepareTransition(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var sut = new NewPartitionTransitionImpl(of(mockStep1, mockStep2), mockContext);
    sut.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    final var secondTerm = 2L;
    final var secondRole = Role.FOLLOWER;

    // when
    sut.transitionTo(DEFAULT_TERM, DEFAULT_ROLE).join();
    sut.transitionTo(secondTerm, secondRole).join();

    // then
    final var invocationRecorder = inOrder(mockStep1, mockStep2);
    // excerpt from call sequence (calls before and after this are covered in other test cases)
    invocationRecorder.verify(mockStep2).transitionTo(mockContext, DEFAULT_TERM, DEFAULT_ROLE);
    invocationRecorder.verify(mockStep2).prepareTransition(mockContext, secondTerm, secondRole);
    invocationRecorder.verify(mockStep1).prepareTransition(mockContext, secondTerm, secondRole);
    invocationRecorder.verify(mockStep1).transitionTo(mockContext, secondTerm, secondRole);
  }

  @Test
  void shouldAbortTransitionIfOneStepThrowsAnExceptionDuringPreparationPhase() {
    // given
    final var secondTerm = 2L;
    final var secondRole = Role.FOLLOWER;

    final var testException = new Exception("TEST_EXCEPTION");
    when(mockStep1.transitionTo(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));
    when(mockStep1.prepareTransition(mockContext, secondTerm, secondRole))
        .thenReturn(TEST_CONCURRENCY_CONTROL.failedFuture(testException));

    when(mockStep2.transitionTo(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));
    when(mockStep2.prepareTransition(any(), anyLong(), any()))
        .thenReturn(TEST_CONCURRENCY_CONTROL.completedFuture(null));

    final var sut = new NewPartitionTransitionImpl(of(mockStep1, mockStep2), mockContext);
    sut.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);

    // when
    final var firstTransitionFuture = sut.transitionTo(DEFAULT_TERM, DEFAULT_ROLE);
    final var secondTransitionFuture = sut.transitionTo(secondTerm, secondRole);

    // then
    assertThat(firstTransitionFuture.isCompletedExceptionally()).isFalse();

    verify(mockStep1, never()).transitionTo(mockContext, secondTerm, secondRole);
    verify(mockStep2, never()).transitionTo(mockContext, secondTerm, secondRole);

    assertThatThrownBy(() -> secondTransitionFuture.join())
        .isInstanceOf(CompletionException.class)
        .getCause()
        .isSameAs(testException);
  }

  private final class WaitingTransitionStep implements PartitionTransitionStep {

    private final ConcurrencyControl concurrencyControl;
    private final CountDownLatch transitionCountDownLatch;

    private WaitingTransitionStep(
        final ConcurrencyControl concurrencyControl,
        final CountDownLatch transitionCountDownLatch) {
      this.concurrencyControl = concurrencyControl;
      this.transitionCountDownLatch = transitionCountDownLatch;
    }

    @Override
    public ActorFuture<Void> prepareTransition(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      final ActorFuture<Void> cleanupFuture = concurrencyControl.createFuture();
      cleanupFuture.complete(null);
      return cleanupFuture;
    }

    @Override
    public ActorFuture<Void> transitionTo(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      final ActorFuture<Void> transitionFuture = concurrencyControl.createFuture();
      final var transitionThread =
          new Thread(
              () -> {
                try {
                  transitionCountDownLatch.await();
                } catch (final InterruptedException e) {
                  LOGGER.error(e.getMessage(), e);
                } finally {
                  transitionFuture.complete(null);
                }
              });
      transitionThread.start();
      return transitionFuture;
    }

    @Override
    public String getName() {
      return "WaitingTransitionStep";
    }
  }
}
