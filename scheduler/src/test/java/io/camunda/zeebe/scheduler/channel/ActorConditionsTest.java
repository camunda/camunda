/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.channel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.scheduler.ActorCondition;
import org.junit.Test;

public final class ActorConditionsTest {

  @Test
  public void shouldAddCondition() {
    // given
    final ActorConditions actorConditions = new ActorConditions();

    // when
    final ActorCondition condition = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition);

    // then
    actorConditions.signalConsumers();
    verify(condition).signal();
  }

  @Test
  public void shouldAddConditions() {
    // given
    final ActorConditions actorConditions = new ActorConditions();

    // when
    final ActorCondition condition1 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition1);

    final ActorCondition condition2 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition2);

    final ActorCondition condition3 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition3);

    // then
    actorConditions.signalConsumers();
    verify(condition1).signal();
    verify(condition2).signal();
    verify(condition3).signal();
  }

  @Test
  public void shouldRemoveCondition() {
    // given
    final ActorConditions actorConditions = new ActorConditions();
    final ActorCondition condition = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition);

    // when
    actorConditions.removeConsumer(condition);

    // then
    actorConditions.signalConsumers();
    verify(condition, never()).signal();
  }

  @Test
  public void shouldRemoveNotRegisteredCondition() {
    // given
    final ActorConditions actorConditions = new ActorConditions();
    final ActorCondition condition = mock(ActorCondition.class);
    final ActorCondition notRegistered = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition);

    // when
    actorConditions.removeConsumer(notRegistered);

    // then
    actorConditions.signalConsumers();
    verify(condition).signal();
  }

  @Test
  public void shouldRemoveConditionInMiddle() {
    // given
    final ActorConditions actorConditions = new ActorConditions();
    final ActorCondition condition1 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition1);

    final ActorCondition condition2 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition2);

    final ActorCondition condition3 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition3);

    // when
    actorConditions.removeConsumer(condition2);

    // then
    actorConditions.signalConsumers();
    verify(condition1).signal();
    verify(condition2, never()).signal();
    verify(condition3).signal();
  }

  @Test
  public void shouldRemoveFirstCondition() {
    // given
    final ActorConditions actorConditions = new ActorConditions();
    final ActorCondition condition1 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition1);

    final ActorCondition condition2 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition2);

    final ActorCondition condition3 = mock(ActorCondition.class);
    actorConditions.registerConsumer(condition3);

    // when
    actorConditions.removeConsumer(condition1);

    // then
    actorConditions.signalConsumers();
    verify(condition1, never()).signal();
    verify(condition2).signal();
    verify(condition3).signal();
  }
}
