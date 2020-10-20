/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.zeebe.broker.system.partitions.impl.PartitionTransitionImpl;
import io.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class ZeebePartitionTransitionIntegrationTest {

  @Rule public ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  private PartitionContext ctx;
  private PartitionTransition transition;

  @Before
  public void setup() {
    ctx = mock(PartitionContext.class);
    final NoopPartitionStep firstComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep secondComponent = spy(new NoopPartitionStep());
    transition =
        spy(new PartitionTransitionImpl(ctx, List.of(firstComponent), List.of(secondComponent)));

    final RaftPartition raftPartition = mock(RaftPartition.class);
    when(raftPartition.id()).thenReturn(new PartitionId("", 0));
    when(raftPartition.getRole()).thenReturn(Role.INACTIVE);

    final CriticalComponentsHealthMonitor healthMonitor =
        mock(CriticalComponentsHealthMonitor.class);

    when(ctx.getRaftPartition()).thenReturn(raftPartition);
    when(ctx.getComponentHealthMonitor()).thenReturn(healthMonitor);
  }

  @Test
  public void shouldTransitionToAndCloseInSequence() {
    // given
    final ZeebePartition partition = new ZeebePartition(ctx, transition);
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.LEADER, 1);
    partition.onNewRole(Role.FOLLOWER, 1);

    // when
    partition.closeAsync().join();

    // then
    final InOrder inOrder = Mockito.inOrder(transition);
    inOrder.verify(transition).toInactive();
    inOrder.verify(transition).toLeader();
    inOrder.verify(transition).toFollower();
    inOrder.verify(transition).toInactive();
  }

  private static class NoopPartitionStep implements PartitionStep {

    @Override
    public ActorFuture<Void> open(final PartitionContext context) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> close(final PartitionContext context) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public String getName() {
      return "NoopComponent";
    }
  }
}
