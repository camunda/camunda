/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateRule;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class EventScopeInstanceStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableEventScopeInstanceState state;

  @Before
  public void setUp() {
    final MutableZeebeState zeebeState = stateRule.getZeebeState();
    state = zeebeState.getEventScopeInstanceState();
  }

  @Test
  public void shouldCreateInterruptingEventScopeInstance() {
    // given
    final long key = 123;

    // when
    state.createIfNotExists(key, Collections.singleton(wrapString("foo")));

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isTrue();
    Assertions.assertThat(instance.isInterrupting(wrapString("foo"))).isTrue();
  }

  @Test
  public void shouldCreateNonInterruptingEventScopeInstance() {
    // given
    final long key = 123;

    // when
    state.createIfNotExists(key, Collections.singleton(wrapString("foo")));

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isTrue();
    Assertions.assertThat(instance.isInterrupting(wrapString("bar"))).isFalse();
  }

  @Test
  public void shouldTriggerInterruptingEventScopeInstance() {
    // given
    final long key = 123;
    final long eventKey = 456;
    final EventTrigger eventTrigger = createEventTrigger();

    state.createIfNotExists(key, Collections.singleton(eventTrigger.getElementId()));

    // when
    triggerEvent(key, eventKey, eventTrigger);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger);

    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  public void shouldTriggerInterruptingEventScopeInstanceOnlyOnce() {
    // given
    final long key = 123;
    final long eventKey1 = 456;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 789;
    final EventTrigger eventTrigger2 = createEventTrigger();

    state.createIfNotExists(key, Collections.singleton(eventTrigger1.getElementId()));

    // when
    triggerEvent(key, eventKey1, eventTrigger1);
    triggerEvent(key, eventKey2, eventTrigger2);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();

    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  public void shouldTriggerNonInterruptingEventScopeInstanceMultipleTimes() {
    // given
    final long key = 123;
    final long eventKey1 = 456;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 789;
    final EventTrigger eventTrigger2 = createEventTrigger();

    state.createIfNotExists(key, Collections.emptyList());

    // when
    triggerEvent(key, eventKey1, eventTrigger1);
    triggerEvent(key, eventKey2, eventTrigger2);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger2);
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();

    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isTrue();
  }

  @Test
  public void shouldTriggerStartEventForNonExistingEventScope() {
    // given
    final long scopeKey = 123;
    final EventTrigger eventTrigger = createEventTrigger();

    // when
    triggerStartEvent(scopeKey, 456, eventTrigger);

    // then
    Assertions.assertThat(state.peekEventTrigger(scopeKey)).isEqualTo(eventTrigger);
  }

  @Test
  public void shouldPeekEventTrigger() {
    // given
    final long key = 123;
    final EventTrigger eventTrigger = createEventTrigger();

    // when
    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(key, 1, eventTrigger);

    // then
    Assertions.assertThat(state.peekEventTrigger(key)).isEqualTo(eventTrigger);
    Assertions.assertThat(state.peekEventTrigger(key)).isEqualTo(eventTrigger);
  }

  @Test
  public void shouldPollEventTrigger() {
    // given
    final long key = 123;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final EventTrigger eventTrigger2 = createEventTrigger();

    // when
    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(key, 1, eventTrigger1);
    triggerEvent(key, 2, eventTrigger2);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger2);
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();
  }

  @Test
  public void shouldPollStartEventTrigger() {
    // given
    final long scopeKey = 123;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final EventTrigger eventTrigger2 = createEventTrigger();

    // when
    triggerStartEvent(scopeKey, 1, eventTrigger1);
    triggerStartEvent(scopeKey, 2, eventTrigger2);

    // then
    Assertions.assertThat(state.pollEventTrigger(scopeKey)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(scopeKey)).isEqualTo(eventTrigger2);
    Assertions.assertThat(state.pollEventTrigger(scopeKey)).isNull();
  }

  @Test
  public void shouldDeleteTrigger() {
    // given
    final long key = 123;
    final long eventKey = 456;

    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(key, eventKey, createEventTrigger());

    // when
    state.deleteTrigger(key, eventKey);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();
  }

  @Test
  public void shouldDeleteStartEventTrigger() {
    // given
    final long scopeKey = 123;
    final EventTrigger eventTrigger1 = createEventTrigger();
    triggerStartEvent(scopeKey, 1, eventTrigger1);

    // when
    state.deleteTrigger(scopeKey, 1);

    // then
    Assertions.assertThat(state.pollEventTrigger(scopeKey)).isNull();
  }

  @Test
  public void shouldDeleteEventScopeAndTriggers() {
    // given
    final long key = 123;

    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(123, 1, createEventTrigger());
    triggerEvent(123, 2, createEventTrigger());
    triggerEvent(123, 3, createEventTrigger());

    // when
    state.deleteInstance(key);

    // then
    Assertions.assertThat(state.getInstance(key)).isNull();
    Assertions.assertThat(state.peekEventTrigger(key)).isNull();
  }

  @Test
  public void shouldDeleteStartEventTriggerOnDeletionOfInstance() {
    // given
    final long scopeKey = 123;
    final EventTrigger eventTrigger1 = createEventTrigger();
    triggerStartEvent(scopeKey, 1, eventTrigger1);

    // when
    state.deleteInstance(scopeKey);

    // then
    Assertions.assertThat(state.pollEventTrigger(scopeKey)).isNull();
  }

  @Test
  public void shouldNotFailOnDeletionOfNonExistingInstance() {
    // given
    final long key = 123;

    // expect no exception
    assertThatNoException().isThrownBy(() -> state.deleteInstance(key));
  }

  @Test
  public void shouldShutdownInstance() {
    // given
    final long key = 123;
    state.createIfNotExists(key, Collections.singleton(wrapString("foo")));

    // when
    state.shutdownInstance(key);

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  public void shouldNotCreateInstanceIfTryingToShutdownNonExistentOne() {
    // given
    final long key = 123;

    // when
    state.shutdownInstance(key);

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance).isNull();
  }

  @Test
  public void shouldResetElementInstance() {
    // given
    final long firstKey = 123;
    final long secondKey = 345;
    final DirectBuffer firstId = wrapString("a");
    final Collection<DirectBuffer> firstIds = Collections.singletonList(firstId);

    // when
    state.createIfNotExists(firstKey, firstIds);
    state.createIfNotExists(secondKey, Collections.emptyList());

    // then
    Assertions.assertThat(state.getInstance(firstKey).isInterrupting(firstId)).isTrue();
    Assertions.assertThat(state.getInstance(secondKey).isInterrupting(firstId)).isFalse();
  }

  private void triggerEvent(
      final long eventScopeKey, final long eventKey, final EventTrigger eventTrigger) {
    state.triggerEvent(
        eventScopeKey, eventKey, eventTrigger.getElementId(), eventTrigger.getVariables());
  }

  private void triggerStartEvent(
      final long eventScopeKey, final long eventKey, final EventTrigger eventTrigger) {
    state.triggerStartEvent(
        eventScopeKey, eventKey, eventTrigger.getElementId(), eventTrigger.getVariables());
  }

  private EventTrigger createEventTrigger() {
    return createEventTrigger(randomString(), randomString());
  }

  private EventTrigger createEventTrigger(final String elementId, final String variables) {
    return new EventTrigger()
        .setElementId(wrapString(elementId))
        .setVariables(wrapString(variables));
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }
}
