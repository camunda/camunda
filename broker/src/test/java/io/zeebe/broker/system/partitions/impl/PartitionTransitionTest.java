/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions.impl;

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
import org.assertj.core.api.Assertions;
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
                          Assertions.assertThat(err).isNull();
                          partitionTransition
                              .toInactive()
                              .onComplete((nothing1, err1) -> Assertions.assertThat(err1).isNull());
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
