/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionTransitionImpl;
import io.camunda.zeebe.broker.system.partitions.impl.RecoverablePartitionTransitionException;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
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
    transition.updateTransitionContext(ctx);

    raft = mock(RaftPartition.class);
    when(raft.id()).thenReturn(new PartitionId("", 0));
    when(raft.getRole()).thenReturn(Role.INACTIVE);
    when(raft.getServer()).thenReturn(mock(RaftPartitionServer.class));

    healthMonitor = mock(CriticalComponentsHealthMonitor.class);

    when(ctx.getPartitionAdminControl()).thenReturn(mock(PartitionAdminControl.class));
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
  public void shouldNotifyPartitionRaftListenersOnBecomingLeader() {
    // given
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(ctx, timeout(1000)).notifyListenersOfBecameRaftLeader(1);
  }

  @Test
  public void shouldNotifyPartitionRaftListenersOnBecomingFollower() {
    // given
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.FOLLOWER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(ctx, timeout(1000)).notifyListenersOfBecameRaftFollower(1);
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
    doNothing().when(failureListener).onRecovered(argThat(HealthReport::isHealthy));

    // make partition healthy
    when(healthMonitor.getHealthReport()).thenReturn(report);
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();

    // when
    partition.addFailureListener(failureListener);
    schedulerRule.workUntilDone();

    // then
    verify(failureListener, only()).onRecovered(any());
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
  public void shouldNotTriggerTransitionOnPartitionTransitionException()
      throws InterruptedException {
    // given
    when(transition.toLeader(anyLong()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RecoverablePartitionTransitionException("something went wrong")));

    when(raft.getRole()).thenReturn(Role.LEADER);
    when(raft.term()).thenReturn(2L);
    when(ctx.getCurrentRole()).thenReturn(Role.FOLLOWER);
    when(ctx.getCurrentTerm()).thenReturn(1L);

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.LEADER, 2);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(transition, raft);
    // expected transition supposed to fail
    order.verify(transition).toLeader(2);
    // after failing leader transition no other
    // transitions are triggered
    order.verify(raft, times(0)).stop();
    order.verify(transition, times(0)).toFollower(anyLong());
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
    when(raft.stop()).then(invocation -> CompletableFuture.completedFuture(null));

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.FOLLOWER, 1);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toFollower(0L);
    order.verify(transition).toInactive(anyLong());
    order.verify(raft).stop();
  }

  @Test
  public void shouldStopRaftOnlyAfterTransitioningToInactive() throws InterruptedException {
    // given
    final CompletableActorFuture<Void> inactiveTransitionCompleted = new CompletableActorFuture<>();

    when(transition.toFollower(anyLong()))
        .thenReturn(CompletableActorFuture.completedExceptionally(new Exception("expected")));
    when(transition.toInactive(anyLong())).then(invocation -> inactiveTransitionCompleted);
    when(raft.getRole()).thenReturn(Role.FOLLOWER);
    when(ctx.getCurrentRole()).thenReturn(Role.FOLLOWER);

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.FOLLOWER, 1);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toFollower(0L);
    order.verify(raft).removeRoleChangeListener(partition);
    order.verify(transition).toInactive(anyLong());
    // raft must be stopped only after inactive transition is completed
    verify(raft, never()).stop();

    inactiveTransitionCompleted.complete(null);
    schedulerRule.workUntilDone();
    verify(raft).stop();
  }

  @Test
  public void shouldGoInactiveIfTransitionHasUnrecoverableFailure() throws InterruptedException {
    // given
    when(transition.toLeader(anyLong()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new UnrecoverableException("expected")));
    when(raft.getRole()).thenReturn(Role.LEADER);
    when(raft.term()).thenReturn(1L);

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(raft.getRole(), raft.term());
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(0L);
    order.verify(raft).stop();
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

  @Test
  public void shouldReportUnhealthyIfTransitionStepIsStuck() {
    // given
    final var transitionStep = mock(PartitionTransitionStep.class);
    final var transition = new PartitionTransitionImpl(List.of(transitionStep));
    final var partition = new ZeebePartition(ctx, transition, List.of(new NoopStartupStep()));
    schedulerRule.submitActor(partition);

    // Run one successful transition so that initial services are installed
    when(transitionStep.prepareTransition(any(), anyLong(), any()))
        .thenReturn(CompletableActorFuture.completed(null));
    when(transitionStep.transitionTo(any(), anyLong(), any()))
        .thenReturn(CompletableActorFuture.completed(null));
    when(ctx.getCurrentRole()).thenReturn(Role.FOLLOWER);
    partition.onNewRole(Role.FOLLOWER, 0);
    schedulerRule.workUntilDone();

    // when -- starting a transition that blocks
    // We need to travel back instead of forward because querying the health status happens outside
    // the actor context and doesn't use the manipulated actor clock. So we change the start time
    // of the transition to be 2 minutes in the past.
    schedulerRule.getClock().addTime(Duration.ofMinutes(-2));
    when(transitionStep.transitionTo(any(), anyLong(), any()))
        .thenReturn(new CompletableActorFuture<>());
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // then
    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    verify(healthMonitor).registerComponent(captor.capture());
    final var healthReport = captor.getValue().getHealthReport();
    assertThat(healthReport.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);
    assertThat(healthReport.getIssue().message())
        .contains("Transition from FOLLOWER on term 0 appears blocked");
  }

  @Test
  public void shouldReportUnhealthyPerDefault() {
    // given
    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    schedulerRule.submitActor(partition);

    // when
    schedulerRule.workUntilDone();

    // then
    verify(healthMonitor).registerComponent(captor.capture());

    final var zeebePartitionHealth = captor.getValue();
    final HealthReport healthReport = zeebePartitionHealth.getHealthReport();

    assertThat(healthReport.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);
    assertThat(healthReport.getIssue().message()).contains("Services not installed");
  }

  @Test
  public void shouldCallOnFailureOnceForSameHealthIssue() {
    // given
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();

    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onFailure(any());

    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    verify(healthMonitor).registerComponent(captor.capture());
    final var zeebePartitionHealth = captor.getValue();
    zeebePartitionHealth.addFailureListener(failureListener);

    when(transition.getHealthIssue())
        .thenReturn(HealthIssue.of("it's over", Instant.ofEpochMilli(1029381923L)));

    // when
    final HealthReport healthReport1 = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport1.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    final HealthReport healthReport2 = zeebePartitionHealth.getHealthReport();

    // then
    assertThat(healthReport1).isEqualTo(healthReport2);
    verify(failureListener, times(1)).onHealthReport(argThat(HealthReport::isUnhealthy));
  }

  @Test
  public void shouldCallOnFailureOnHealthIssueChange() {
    // given
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();

    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onFailure(any());

    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    verify(healthMonitor).registerComponent(captor.capture());
    final var zeebePartitionHealth = captor.getValue();
    zeebePartitionHealth.addFailureListener(failureListener);

    when(transition.getHealthIssue())
        .thenReturn(HealthIssue.of("it's over", Instant.ofEpochMilli(1029381923L)));

    // when
    final HealthReport healthReport1 = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport1.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    when(transition.getHealthIssue())
        .thenReturn(HealthIssue.of("it's something else", Instant.ofEpochMilli(1029381923L)));
    final HealthReport healthReport2 = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport2.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // then
    assertThat(healthReport1).isNotEqualTo(healthReport2);
    verify(failureListener, times(2)).onHealthReport(argThat(HealthReport::isUnhealthy));
  }

  @Test
  public void shouldCallOnRecoveredOnceWhenHealthy() {
    // given
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();

    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onFailure(any());
    doNothing().when(failureListener).onRecovered(any());

    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    verify(healthMonitor).registerComponent(captor.capture());
    final var zeebePartitionHealth = captor.getValue();
    zeebePartitionHealth.addFailureListener(failureListener);

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    final HealthReport healthReport1 = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport1.getStatus()).isEqualTo(HealthStatus.HEALTHY);

    final HealthReport healthReport2 = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport2.getStatus()).isEqualTo(HealthStatus.HEALTHY);
    // then
    assertThat(healthReport1).isEqualTo(healthReport2);
    verify(failureListener, times(1)).onHealthReport(argThat(HealthReport::isHealthy));
  }

  @Test
  public void shouldReportHealthyAfterTransition() {
    // given
    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(healthMonitor).registerComponent(captor.capture());

    final var zeebePartitionHealth = captor.getValue();
    final HealthReport healthReport = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport.getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldReportUnhealthyWhenNoDiskAvailable() {
    // given
    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // when
    partition.onDiskSpaceNotAvailable();
    schedulerRule.workUntilDone();

    // then
    verify(healthMonitor).registerComponent(captor.capture());

    final var zeebePartitionHealth = captor.getValue();
    final HealthReport healthReport = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);
    assertThat(healthReport.getIssue().message()).contains("Not enough disk space available");
  }

  @Test
  public void shouldReportHealthyWhenDiskIsAvailableAgain() {
    // given
    final var captor = ArgumentCaptor.forClass(ZeebePartitionHealth.class);
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.LEADER, 1);
    partition.onDiskSpaceNotAvailable();
    schedulerRule.workUntilDone();

    // when
    partition.onDiskSpaceAvailable();
    schedulerRule.workUntilDone();

    // then
    verify(healthMonitor).registerComponent(captor.capture());

    final var zeebePartitionHealth = captor.getValue();
    final HealthReport healthReport = zeebePartitionHealth.getHealthReport();
    assertThat(healthReport.getStatus()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(healthReport.getIssue()).isNull();
  }

  @Test
  public void shouldBeAbleToGetHealthReportFromClosedPartition() {
    // given
    final HealthReport healthReport = mock(HealthReport.class);
    when(healthMonitor.getHealthReport()).thenReturn(healthReport);
    schedulerRule.submitActor(partition);

    // when
    final var closeFuture = partition.closeAsync();
    schedulerRule.workUntilDone();

    // then
    Awaitility.await().until(closeFuture::isDone);
    assertThat(partition.getHealthReport()).isEqualTo(healthReport);
  }

  private static final class NoopStartupStep implements PartitionStartupStep {

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

  private static final class NoopTransitionStep implements PartitionTransitionStep {

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
