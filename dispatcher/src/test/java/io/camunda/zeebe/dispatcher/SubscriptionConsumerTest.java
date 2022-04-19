/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dispatcher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.zeebe.dispatcher.impl.log.LogBuffer;
import io.camunda.zeebe.util.sched.ActorCondition;
import org.junit.jupiter.api.Test;

final class SubscriptionConsumerTest {

  @Test
  void consumersAreSignaledAfterRegistering() {
    // given
    final var consumer = mock(ActorCondition.class);
    final var subscription =
        new Subscription(
            mock(AtomicPosition.class),
            mock(AtomicPosition.class),
            0,
            "",
            mock(ActorCondition.class),
            mock(LogBuffer.class));

    // when
    subscription.registerConsumer(consumer);

    // then
    subscription.getActorConditions().signalConsumers();
    verify(consumer).signal();
  }

  @Test
  void consumersAreNotSignaledAfterRemoving() {
    // given
    final var consumer = mock(ActorCondition.class);
    final var subscription =
        new Subscription(
            mock(AtomicPosition.class),
            mock(AtomicPosition.class),
            0,
            "",
            mock(ActorCondition.class),
            mock(LogBuffer.class));
    subscription.registerConsumer(consumer);

    // when
    subscription.removeConsumer(consumer);

    // then
    subscription.getActorConditions().signalConsumers();
    verifyNoInteractions(consumer);
  }
}
