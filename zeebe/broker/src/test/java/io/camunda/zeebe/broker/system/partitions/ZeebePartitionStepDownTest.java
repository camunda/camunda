/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionTransitionImpl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.health.ComponentTreeListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ZeebePartitionStepDownTest {

  @RegisterExtension
  private final ControlledActorSchedulerExtension schedulerRule =
      new ControlledActorSchedulerExtension();

  private PartitionStartupAndTransitionContextImpl ctx;
  private RaftPartition raft;
  private ZeebePartition partition;

  @BeforeEach
  void setup() {
    ctx = mock(PartitionStartupAndTransitionContextImpl.class);
    final var transition = spy(new PartitionTransitionImpl(List.of()));
    transition.updateTransitionContext(ctx);

    raft = mock(RaftPartition.class);
    when(raft.id()).thenReturn(new PartitionId("", 0));
    when(raft.getRole()).thenReturn(Role.INACTIVE);
    when(raft.getServer()).thenReturn(mock(RaftPartitionServer.class));

    final var healthMonitor = mock(CriticalComponentsHealthMonitor.class);
    final var brokerHealthCheckService = mock(BrokerHealthCheckService.class);
    when(brokerHealthCheckService.componentName()).thenReturn("Broker-0");

    when(ctx.getPartitionAdminControl()).thenReturn(mock(PartitionAdminControl.class));
    when(ctx.getRaftPartition()).thenReturn(raft);
    when(ctx.getPartitionContext()).thenReturn(ctx);
    when(ctx.getComponentHealthMonitor()).thenReturn(healthMonitor);
    when(ctx.createTransitionContext()).thenReturn(ctx);
    when(ctx.brokerHealthCheckService()).thenReturn(brokerHealthCheckService);
    when(ctx.getComponentTreeListener()).thenReturn(ComponentTreeListener.noop());
    when(ctx.getPartitionStartupMeterRegistry()).thenReturn(new SimpleMeterRegistry());
    when(ctx.getPartitionId()).thenReturn(1);

    partition = new ZeebePartition(ctx, transition, List.of(new NoopStartupStep()));
  }

  @Test
  void shouldStepDownWhenLeaderReceivesOnRecoverableFailure() {
    // given — partition is acting as leader
    when(ctx.getCurrentRole()).thenReturn(Role.LEADER);
    when(ctx.getCurrentTerm()).thenReturn(1L);
    when(raft.term()).thenReturn(1L);
    when(raft.stepDown()).thenReturn(CompletableFuture.completedFuture(null));

    schedulerRule.submitActor(partition);

    // when — a component (e.g. stream processor) signals a recoverable failure requiring step-down
    final var report =
        HealthReport.unhealthy(partition).withMessage("transaction conflict", Instant.now());
    partition.onRecoverableFailure(report);

    schedulerRule.workUntilDone();

    // then — the Raft partition should step down so a new leader can be elected
    verify(raft).stepDown();
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
}
