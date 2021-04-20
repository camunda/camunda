/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.zeebe.util.exception.UnrecoverableException;
import io.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.zeebe.util.health.FailureListener;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

public class ZeebePartitionTest {

  @Rule public ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private PartitionContext ctx;
  private PartitionTransition transition;
  private CriticalComponentsHealthMonitor healthMonitor;
  private RaftPartition raft;

  @Before
  public void setup() {
    ctx = mock(PartitionContext.class);
    transition = spy(new NoopTransition());

    raft = mock(RaftPartition.class);
    when(raft.id()).thenReturn(new PartitionId("", 0));
    when(raft.getRole()).thenReturn(Role.INACTIVE);

    healthMonitor = mock(CriticalComponentsHealthMonitor.class);

    when(ctx.getRaftPartition()).thenReturn(raft);
    when(ctx.getComponentHealthMonitor()).thenReturn(healthMonitor);
  }

  @Test
  public void shouldInstallLeaderPartition() {
    // given
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(transition).toLeader(1);
  }

  @Test
  public void shouldCallOnFailureOnAddFailureListenerAndUnhealthy() {
    // given
    when(healthMonitor.getHealthStatus()).thenReturn(HealthStatus.UNHEALTHY);
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);
    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onFailure();

    // when
    partition.addFailureListener(failureListener);
    schedulerRule.workUntilDone();

    // then
    verify(failureListener, only()).onFailure();
  }

  @Test
  public void shouldCallOnRecoveredOnAddFailureListenerAndHealthy() {
    // given
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);
    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onRecovered();
    // make partition healthy
    when(healthMonitor.getHealthStatus()).thenReturn(HealthStatus.HEALTHY);
    schedulerRule.workUntilDone();

    // when
    partition.addFailureListener(failureListener);
    schedulerRule.workUntilDone();

    // then
    verify(failureListener, only()).onRecovered();
  }

  @Test
  public void shouldStepDownAfterFailedLeaderTransition() throws InterruptedException {
    // given
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);
    final CountDownLatch latch = new CountDownLatch(1);

    when(transition.toLeader(anyLong()))
        .thenReturn(CompletableActorFuture.completedExceptionally(new Exception("expected")));
    when(transition.toFollower(anyLong()))
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.LEADER);
    when(raft.stepDown())
        .then(
            invocation -> {
              partition.onNewRole(Role.FOLLOWER, 2);
              return CompletableFuture.completedFuture(null);
            });

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(1);
    order.verify(raft).stepDown();
    order.verify(transition).toFollower(2);
  }

  @Test
  public void shouldGoInactiveAfterFailedFollowerTransition() throws InterruptedException {
    // given
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);
    final CountDownLatch latch = new CountDownLatch(1);

    when(transition.toFollower(anyLong()))
        .thenReturn(CompletableActorFuture.completedExceptionally(new Exception("expected")));
    when(transition.toInactive())
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.FOLLOWER);
    when(raft.goInactive())
        .then(
            invocation -> {
              partition.onNewRole(Role.INACTIVE, 2);
              return CompletableFuture.completedFuture(null);
            });

    // when
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toFollower(0L);
    order.verify(raft).goInactive();
    order.verify(transition).toInactive();
  }

  @Test
  public void shouldGoInactiveIfTransitionHasUnrecoverableFailure() throws InterruptedException {
    // given
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);
    final CountDownLatch latch = new CountDownLatch(1);

    when(transition.toLeader(anyLong()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new UnrecoverableException("expected")));
    when(transition.toInactive())
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.LEADER);

    // when
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(0L);
    order.verify(transition).toInactive();
    order.verify(raft).goInactive();
    order.verifyNoMoreInteractions();
  }

  private static class NoopTransition implements PartitionTransition {

    @Override
    public ActorFuture<Void> toFollower(final long currentTerm) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> toLeader(final long currentTerm) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> toInactive() {
      return CompletableActorFuture.completed(null);
    }
  }
}
