/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
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
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionTransitionImpl;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

public class ZeebePartitionTest {

  @Rule public ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private PartitionStartupAndTransitionContextImpl ctx;
  private PartitionTransition transition;
  private CriticalComponentsHealthMonitor healthMonitor;
  private RaftPartition raft;
  private ZeebePartition partition;

  @Before
  public void setup() {
    ctx = mock(PartitionStartupAndTransitionContextImpl.class);
    transition = spy(new PartitionTransitionImpl(List.of(new NoopTransitionStep())));

    raft = mock(RaftPartition.class);
    when(raft.id()).thenReturn(new PartitionId("", 0));
    when(raft.getRole()).thenReturn(Role.INACTIVE);
    when(raft.getServer()).thenReturn(mock(RaftPartitionServer.class));

    healthMonitor = mock(CriticalComponentsHealthMonitor.class);

    when(ctx.getRaftPartition()).thenReturn(raft);
    when(ctx.getPartitionContext()).thenReturn(ctx);
    when(ctx.getComponentHealthMonitor()).thenReturn(healthMonitor);
    when(ctx.createTransitionContext()).thenReturn(ctx);

    partition = new ZeebePartition(ctx, transition, List.of(new NoopStartupStep()));
  }

  @Test
  public void shouldInstallLeaderPartition() {
    // given
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(transition).toLeader(1);
  }

  @Test
  public void shouldInstallFollowerPartition() {
    // given
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.FOLLOWER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(transition).toFollower(1);
  }

  @Test
  public void shouldUpdateCurrentTermAndRoleAfterTransition() {
    // given
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();

    // when
    partition.onNewRole(Role.FOLLOWER, 2);
    schedulerRule.workUntilDone();

    // then
    verify(ctx, atLeast(1)).setCurrentRole(Role.FOLLOWER);
    verify(ctx, atLeast(1)).setCurrentTerm(2);
  }

  @Test
  public void shouldUseCurrentTermForInactiveTransition() {
    // given
    schedulerRule.submitActor(partition);
    when(ctx.getCurrentTerm()).thenReturn(3L);

    // when
    // term given for inactive role is ignored, hence we pass -1 here to verify that it uses
    // the term from the context. In reality the term passes here will be a valid term.
    partition.onNewRole(Role.INACTIVE, -1);
    schedulerRule.workUntilDone();

    // then
    verify(transition, atLeast(1)).toInactive(3);
  }

  @Test
  public void shouldCallOnFailureOnAddFailureListenerAndUnhealthy() {
    // given
    final var report = mock(HealthReport.class);
    when(report.getStatus()).thenReturn(HealthStatus.UNHEALTHY);
    when(healthMonitor.getHealthReport()).thenReturn(report);
    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onFailure(any());
    schedulerRule.submitActor(partition);

    // when
    partition.addFailureListener(failureListener);
    schedulerRule.workUntilDone();

    // then
    verify(failureListener, only()).onFailure(any());
  }

  @Test
  public void shouldCallOnRecoveredOnAddFailureListenerAndHealthy() {
    // given
    final var report = mock(HealthReport.class);
    when(report.getStatus()).thenReturn(HealthStatus.HEALTHY);
    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onRecovered();

    // make partition healthy
    when(healthMonitor.getHealthReport()).thenReturn(report);
    schedulerRule.submitActor(partition);
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
    when(raft.term()).thenReturn(1L);
    when(ctx.getCurrentRole()).thenReturn(Role.LEADER);
    when(ctx.getCurrentTerm()).thenReturn(1L);
    when(raft.stepDown())
        .then(
            invocation -> {
              partition.onNewRole(Role.FOLLOWER, 1);
              return CompletableFuture.completedFuture(null);
            });

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(1);
    order.verify(raft).stepDown();
    order.verify(transition).toFollower(1);
  }

  @Test
  public void shouldGoInactiveAfterFailedFollowerTransition() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);

    when(transition.toFollower(anyLong()))
        .thenReturn(CompletableActorFuture.completedExceptionally(new Exception("expected")));
    when(transition.toInactive(anyLong()))
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.FOLLOWER);
    when(ctx.getCurrentRole()).thenReturn(Role.FOLLOWER);
    when(raft.goInactive())
        .then(
            invocation -> {
              partition.onNewRole(Role.INACTIVE, 2);
              return CompletableFuture.completedFuture(null);
            });

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.FOLLOWER, 1);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toFollower(0L);
    order.verify(raft).goInactive();
    order.verify(transition).toInactive(anyLong());
  }

  @Test
  public void shouldGoInactiveIfTransitionHasUnrecoverableFailure() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    when(transition.toLeader(anyLong()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new UnrecoverableException("expected")));
    when(transition.toInactive(anyLong()))
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.LEADER);
    when(raft.term()).thenReturn(1L);

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(raft.getRole(), raft.term());
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(0L);
    order.verify(transition).toInactive(anyLong());
    order.verify(raft).goInactive();
  }

  @Test
  public void shouldCloseZeebePartitionWhileOngoingTransition() {
    // given
    final PartitionTransitionStep mockTransitionStep = mock(PartitionTransitionStep.class);
    final CompletableActorFuture<Void> firstTransitionFuture = new CompletableActorFuture<>();
    final CompletableActorFuture<Void> secondTransitionFuture = new CompletableActorFuture<>();
    when(mockTransitionStep.transitionTo(any(), anyLong(), any(Role.class)))
        .thenReturn(firstTransitionFuture)
        .thenReturn(secondTransitionFuture)
        .thenReturn(CompletableActorFuture.completed(null));

    when(mockTransitionStep.prepareTransition(any(), anyLong(), any(Role.class)))
        .thenReturn(CompletableActorFuture.completed(null));

    transition =
        spy(new PartitionTransitionImpl(List.of(mockTransitionStep, new NoopTransitionStep())));
    partition = new ZeebePartition(ctx, transition, List.of(new NoopStartupStep()));
    schedulerRule.submitActor(partition);

    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // when
    final var closeFuture = partition.closeAsync();
    schedulerRule.workUntilDone();
    partition.onNewRole(Role.FOLLOWER, 2);
    schedulerRule.workUntilDone();
    firstTransitionFuture.complete(null);
    secondTransitionFuture.complete(null);
    schedulerRule.workUntilDone();

    // then
    Awaitility.await().until(closeFuture::isDone);
  }

  private static class NoopStartupStep implements PartitionStartupStep {

    @Override
    public String getName() {
      return "noop";
    }

    @Override
    public ActorFuture<PartitionStartupContext> startup(
        final PartitionStartupContext partitionStartupContext) {
      return CompletableActorFuture.completed(partitionStartupContext);
    }

    @Override
    public ActorFuture<PartitionStartupContext> shutdown(
        final PartitionStartupContext partitionStartupContext) {
      return CompletableActorFuture.completed(partitionStartupContext);
    }
  }

  private static class NoopTransitionStep implements PartitionTransitionStep {

    @Override
    public ActorFuture<Void> prepareTransition(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> transitionTo(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public String getName() {
      return "noop-transition-step";
    }
  }
}
