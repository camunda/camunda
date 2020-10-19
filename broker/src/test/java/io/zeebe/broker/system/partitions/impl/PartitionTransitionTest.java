/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

public class PartitionTransitionTest {

  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private PartitionContext ctx;

  @Before
  public void setup() {
    ctx = mock(PartitionContext.class);
    when(ctx.getPartitionId()).thenReturn(0);
  }

  @Test
  public void shouldCloseInOppositeOrderOfOpen() {
    // given
    final NoopPartitionStep firstComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep secondComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(
            ctx, List.of(firstComponent, secondComponent), Collections.EMPTY_LIST);

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl ->
                partitionTransition
                    .toLeader()
                    .onComplete(
                        (nothing, err) -> {
                          assertThat(err).isNull();
                          partitionTransition
                              .toInactive()
                              .onComplete((nothing1, err1) -> assertThat(err1).isNull());
                        }));

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(firstComponent, secondComponent);
    order.verify(firstComponent).open(ctx);
    order.verify(secondComponent).open(ctx);
    order.verify(secondComponent).close(ctx);
    order.verify(firstComponent).close(ctx);
  }

  @Test
  public void shouldTransitionFromLeaderToFollowerInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toLeader();
              partitionTransition.toFollower();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(leaderComponent, followerComponent);
    order.verify(leaderComponent).open(ctx);
    order.verify(leaderComponent).close(ctx);
    order.verify(followerComponent).open(ctx);
    order.verifyNoMoreInteractions();
  }

  @Test
  public void shouldTransitionFromFollowerToLeaderInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toFollower();
              partitionTransition.toLeader();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(leaderComponent, followerComponent);
    order.verify(followerComponent).open(ctx);
    order.verify(followerComponent).close(ctx);
    order.verify(leaderComponent).open(ctx);
    order.verifyNoMoreInteractions();
  }

  @Test
  public void shouldTransitionFromFollowerToInactiveInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toFollower();
              partitionTransition.toInactive();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(leaderComponent, followerComponent);
    order.verify(followerComponent).open(ctx);
    order.verify(followerComponent).close(ctx);
  }

  @Test
  public void shouldTransitionFromLeaderToInactiveInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toLeader();
              partitionTransition.toInactive();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(leaderComponent);
    order.verify(leaderComponent).open(ctx);
    order.verify(leaderComponent).close(ctx);
  }

  @Test
  public void shouldTransitionFromInactiveToLeaderInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toInactive();
              partitionTransition.toLeader();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(leaderComponent);
    order.verify(leaderComponent).open(ctx);
  }

  @Test
  public void shouldTransitionFromInactiveToFollowerInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toInactive();
              partitionTransition.toFollower();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(followerComponent);
    order.verify(followerComponent).open(ctx);
  }

  @Test
  public void shouldTransitionFromLeaderToLeaderInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toLeader();
              partitionTransition.toLeader();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(leaderComponent);
    order.verify(leaderComponent).open(ctx);
    order.verify(leaderComponent).close(ctx);
    order.verify(leaderComponent).open(ctx);
  }

  @Test
  public void shouldTransitionFromFollowerToFollowerInSequence() {
    // given
    final NoopPartitionStep leaderComponent = spy(new NoopPartitionStep());
    final NoopPartitionStep followerComponent = spy(new NoopPartitionStep());
    final PartitionTransitionImpl partitionTransition =
        new PartitionTransitionImpl(ctx, List.of(leaderComponent), List.of(followerComponent));

    // when
    final Actor actor =
        Actor.wrap(
            actorCtrl -> {
              partitionTransition.toFollower();
              partitionTransition.toFollower();
            });

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    final InOrder order = inOrder(followerComponent);
    order.verify(followerComponent).open(ctx);
    order.verify(followerComponent).close(ctx);
    order.verify(followerComponent).open(ctx);
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
