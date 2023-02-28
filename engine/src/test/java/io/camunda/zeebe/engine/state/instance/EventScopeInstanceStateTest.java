/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class EventScopeInstanceStateTest {

  private static final int SCOPE_KEY = 123;
  private static final Collection<DirectBuffer> NO_INTERRUPTING_ELEMENT_IDS =
      Collections.emptySet();
  private static final Collection<DirectBuffer> NO_BOUNDARY_ELEMENT_IDS = Collections.emptySet();

  private MutableProcessingState processingState;

  private MutableEventScopeInstanceState state;

  @BeforeEach
  void setUp() {
    state = processingState.getEventScopeInstanceState();
  }

  @Test
  void shouldCreateInterruptingEventScopeInstance() {
    // given
    final var interruptingElementId = wrapString("interrupting");
    final var interruptingElementIds = Collections.singleton(interruptingElementId);

    // when
    state.createInstance(SCOPE_KEY, interruptingElementIds, NO_BOUNDARY_ELEMENT_IDS);

    // then
    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();
    assertThat(instance.isInterruptingElementId(interruptingElementId)).isTrue();
    assertThat(instance.isBoundaryElementId(interruptingElementId)).isFalse();
  }

  @Test
  void shouldCreateNonInterruptingEventScopeInstance() {
    // given
    final var nonInterruptingElementId = wrapString("non-interrupting");

    // when
    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);

    // then
    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();
    assertThat(instance.isInterruptingElementId(nonInterruptingElementId)).isFalse();
    assertThat(instance.isBoundaryElementId(nonInterruptingElementId)).isFalse();
  }

  @Test
  void shouldCreateEventScopeInstanceWithBoundaryEvents() {
    // given
    final var nonInterruptingBoundaryElementId = wrapString("non-interrupting-boundary");
    final var interruptingBoundaryElementId = wrapString("interrupting-boundary");
    final var boundaryElementIds =
        Set.of(nonInterruptingBoundaryElementId, interruptingBoundaryElementId);
    final var interruptingElementIds = Set.of(interruptingBoundaryElementId);

    // when
    state.createInstance(SCOPE_KEY, interruptingElementIds, boundaryElementIds);

    // then
    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();

    assertThat(instance.isInterruptingElementId(nonInterruptingBoundaryElementId)).isFalse();
    assertThat(instance.isBoundaryElementId(nonInterruptingBoundaryElementId)).isTrue();

    assertThat(instance.isInterruptingElementId(interruptingBoundaryElementId)).isTrue();
    assertThat(instance.isBoundaryElementId(interruptingBoundaryElementId)).isTrue();
  }

  @Test
  void shouldCreateInterruptingEventScopeInstanceWithBoundaryEvents() {
    // given
    final var interruptingElementId = wrapString("interrupting");
    final var interruptingElementIds = Set.of(interruptingElementId);
    final var boundaryElementId = wrapString("non-interrupting-boundary");
    final var boundaryElementIds = Set.of(boundaryElementId);

    // when
    state.createInstance(SCOPE_KEY, interruptingElementIds, boundaryElementIds);

    // then
    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();

    assertThat(instance.isInterruptingElementId(interruptingElementId)).isTrue();
    assertThat(instance.isBoundaryElementId(interruptingElementId)).isFalse();

    assertThat(instance.isInterruptingElementId(boundaryElementId)).isFalse();
    assertThat(instance.isBoundaryElementId(boundaryElementId)).isTrue();
  }

  @Test
  void shouldAllowTriggeringEventForNonInterruptingScope() {
    // given
    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);

    // when
    final var canTriggerEvent = state.canTriggerEvent(SCOPE_KEY, wrapString("non-interrupting"));

    // then
    assertThat(canTriggerEvent).isTrue();
  }

  @Test
  void shouldNotAllowTriggeringEventForInterruptedScope() {
    // given
    final var interruptingElementId = wrapString("interrupting");
    state.createInstance(SCOPE_KEY, Set.of(interruptingElementId), NO_BOUNDARY_ELEMENT_IDS);

    final var canInterruptScope = state.canTriggerEvent(SCOPE_KEY, interruptingElementId);
    assertThat(canInterruptScope).isTrue();

    final var eventTrigger = createEventTrigger(bufferAsString(interruptingElementId), "");
    triggerEvent(SCOPE_KEY, 123, eventTrigger, -1L);

    // when
    final var canTriggerEventAgain = state.canTriggerEvent(SCOPE_KEY, interruptingElementId);
    final var canTriggerNonInterruptingEvent =
        state.canTriggerEvent(SCOPE_KEY, wrapString("non-interrupting"));

    // then
    assertThat(canTriggerEventAgain).isFalse();
    assertThat(canTriggerNonInterruptingEvent).isFalse();
  }

  @Test
  void shouldAllowTriggeringBoundaryEventForInterruptedScope() {
    // given
    final var interruptingElementId = wrapString("interrupting");
    final var interruptingBoundaryElementId = wrapString("interrupting-boundary");
    final var nonInterruptingBoundaryElementId = wrapString("non-interrupting-boundary");
    state.createInstance(
        SCOPE_KEY,
        Set.of(interruptingElementId, interruptingBoundaryElementId),
        Set.of(interruptingBoundaryElementId, nonInterruptingBoundaryElementId));

    final var canInterruptScope = state.canTriggerEvent(SCOPE_KEY, interruptingElementId);
    assertThat(canInterruptScope).isTrue();

    final var eventTrigger = createEventTrigger(bufferAsString(interruptingElementId), "");
    triggerEvent(SCOPE_KEY, 123, eventTrigger, -1L);

    // when
    final var canTriggerInterruptingBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, interruptingBoundaryElementId);
    final var canTriggerNonInterruptingBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, nonInterruptingBoundaryElementId);

    // then
    assertThat(canTriggerInterruptingBoundaryEvent).isTrue();
    assertThat(canTriggerNonInterruptingBoundaryEvent).isTrue();
  }

  @Test
  void shouldAllowTriggeringAfterNonInterruptingBoundaryEvents() {
    // given
    final var interruptingElementId = wrapString("interrupting");
    final var interruptingBoundaryElementId = wrapString("interrupting-boundary");
    final var nonInterruptingBoundaryElementId = wrapString("non-interrupting-boundary");
    state.createInstance(
        SCOPE_KEY,
        Set.of(interruptingElementId),
        Set.of(interruptingBoundaryElementId, nonInterruptingBoundaryElementId));

    final var canTriggerBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, nonInterruptingBoundaryElementId);
    assertThat(canTriggerBoundaryEvent).isTrue();

    final var eventTrigger =
        createEventTrigger(bufferAsString(nonInterruptingBoundaryElementId), "");
    triggerEvent(SCOPE_KEY, 123, eventTrigger, -1L);

    // when
    final var canTriggerInterruptingBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, interruptingBoundaryElementId);
    final var canTriggerNonInterruptingBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, nonInterruptingBoundaryElementId);
    final var canTriggerInterruptingEvent = state.canTriggerEvent(SCOPE_KEY, interruptingElementId);
    final var canTriggerNonInterruptingEvent =
        state.canTriggerEvent(SCOPE_KEY, wrapString("non-interrupting"));

    // then
    assertThat(canTriggerInterruptingBoundaryEvent).isTrue();
    assertThat(canTriggerNonInterruptingBoundaryEvent).isTrue();
    assertThat(canTriggerInterruptingEvent).isTrue();
    assertThat(canTriggerNonInterruptingEvent).isTrue();
  }

  @Test
  void shouldNotAllowTriggeringEventAfterInterruptingBoundaryEvent() {
    // given
    final var interruptingElementId = wrapString("interrupting");
    final var interruptingBoundaryElementId = wrapString("interrupting-boundary");
    final var nonInterruptingBoundaryElementId = wrapString("non-interrupting-boundary");
    state.createInstance(
        SCOPE_KEY,
        Set.of(interruptingElementId, interruptingBoundaryElementId),
        Set.of(interruptingBoundaryElementId, nonInterruptingBoundaryElementId));

    final var canTriggerBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, interruptingBoundaryElementId);
    assertThat(canTriggerBoundaryEvent).isTrue();

    final var eventTrigger = createEventTrigger(bufferAsString(interruptingBoundaryElementId), "");
    triggerEvent(SCOPE_KEY, 123, eventTrigger, -1L);

    // when
    final var canTriggerInterruptingBoundaryEventAgain =
        state.canTriggerEvent(SCOPE_KEY, interruptingBoundaryElementId);
    final var canTriggerNonInterruptingBoundaryEvent =
        state.canTriggerEvent(SCOPE_KEY, nonInterruptingBoundaryElementId);
    final var canTriggerInterruptingEvent = state.canTriggerEvent(SCOPE_KEY, interruptingElementId);
    final var canTriggerNonInterruptingEvent =
        state.canTriggerEvent(SCOPE_KEY, wrapString("non-interrupting"));

    // then
    assertThat(canTriggerInterruptingBoundaryEventAgain).isFalse();
    assertThat(canTriggerNonInterruptingBoundaryEvent).isFalse();
    assertThat(canTriggerInterruptingEvent).isFalse();
    assertThat(canTriggerNonInterruptingEvent).isFalse();
  }

  @Test
  void shouldTriggerInterruptingEventScopeInstance() {
    // given
    final long eventKey = 456;
    final EventTrigger eventTrigger = createEventTrigger();
    final var interruptingElementIds = Collections.singleton(eventTrigger.getElementId());

    state.createInstance(SCOPE_KEY, interruptingElementIds, NO_BOUNDARY_ELEMENT_IDS);

    // when
    triggerEvent(SCOPE_KEY, eventKey, eventTrigger, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger);

    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();
    assertThat(instance.isInterrupted()).isTrue();
  }

  @Test
  void shouldTriggerInterruptingEventScopeInstanceOnlyOnce() {
    // given
    final long eventKey1 = 456;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 789;
    final EventTrigger eventTrigger2 = createEventTrigger();

    final var interruptingElementIds = Collections.singleton(eventTrigger1.getElementId());

    state.createInstance(SCOPE_KEY, interruptingElementIds, NO_BOUNDARY_ELEMENT_IDS);

    // when
    triggerEvent(SCOPE_KEY, eventKey1, eventTrigger1, -1L);
    triggerEvent(SCOPE_KEY, eventKey2, eventTrigger2, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();

    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();
    assertThat(instance.isInterrupted()).isTrue();
  }

  @Test
  void shouldTriggerNonInterruptingEventScopeInstanceMultipleTimes() {
    // given
    final long eventKey1 = 456;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 789;
    final EventTrigger eventTrigger2 = createEventTrigger();

    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);

    // when
    triggerEvent(SCOPE_KEY, eventKey1, eventTrigger1, -1L);
    triggerEvent(SCOPE_KEY, eventKey2, eventTrigger2, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger2);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();

    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isAccepting()).isTrue();
    assertThat(instance.isInterrupted()).isFalse();
  }

  @Test
  void shouldTriggerInterruptingEventScopeAndNonInterruptingBoundaryEvent() {
    // given
    final long eventKey1 = 1;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 2;
    final EventTrigger eventTrigger2 = createEventTrigger();

    final var interruptingIds = Set.of(eventTrigger1.getElementId());
    final var boundaryElementIds = Set.of(eventTrigger2.getElementId());
    state.createInstance(SCOPE_KEY, interruptingIds, boundaryElementIds);

    // when
    triggerEvent(SCOPE_KEY, eventKey1, eventTrigger1, -1L);
    triggerEvent(SCOPE_KEY, eventKey2, eventTrigger2, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger2);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();

    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isInterrupted()).isTrue();
    assertThat(instance.isAccepting()).isTrue();
  }

  @Test
  void shouldTriggerInterruptingEventScopeAndInterruptingBoundaryEvent() {
    // given
    final long eventKey1 = 1;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 2;
    final EventTrigger eventTrigger2 = createEventTrigger();

    final var interruptingIds = Set.of(eventTrigger1.getElementId(), eventTrigger2.getElementId());
    final var boundaryElementIds = Set.of(eventTrigger2.getElementId());
    state.createInstance(SCOPE_KEY, interruptingIds, boundaryElementIds);

    // when
    triggerEvent(SCOPE_KEY, eventKey1, eventTrigger1, -1L);
    triggerEvent(SCOPE_KEY, eventKey2, eventTrigger2, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger2);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();

    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isInterrupted()).isTrue();
    assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  void shouldTriggerInterruptingBoundaryEventOnlyOnce() {
    // given
    final long eventKey1 = 1;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 2;
    final EventTrigger eventTrigger2 = createEventTrigger();
    final long eventKey3 = 3;
    final EventTrigger eventTrigger3 = createEventTrigger();

    final var interruptingIds = Set.of(eventTrigger1.getElementId(), eventTrigger3.getElementId());
    final var boundaryElementIds =
        Set.of(eventTrigger1.getElementId(), eventTrigger2.getElementId());
    state.createInstance(SCOPE_KEY, interruptingIds, boundaryElementIds);

    // when
    triggerEvent(SCOPE_KEY, eventKey1, eventTrigger1, -1L);
    triggerEvent(SCOPE_KEY, eventKey2, eventTrigger2, -1L);
    triggerEvent(SCOPE_KEY, eventKey3, eventTrigger3, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();

    final EventScopeInstance instance = state.getInstance(SCOPE_KEY);
    assertThat(instance.isInterrupted()).isTrue();
    assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  void shouldTriggerStartEventForNonExistingEventScope() {
    // given
    final EventTrigger eventTrigger = createEventTrigger();

    // when
    triggerStartEvent(SCOPE_KEY, 456, eventTrigger, -1L);

    // then
    assertThat(state.peekEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger);
  }

  @Test
  void shouldPeekEventTrigger() {
    // given
    final EventTrigger eventTrigger = createEventTrigger();

    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);

    // when
    triggerEvent(SCOPE_KEY, 1, eventTrigger, -1L);

    // then
    assertThat(state.peekEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger);
    assertThat(state.peekEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger);
  }

  @Test
  void shouldPollEventTrigger() {
    // given
    final EventTrigger eventTrigger1 = createEventTrigger();
    final EventTrigger eventTrigger2 = createEventTrigger();

    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_INTERRUPTING_ELEMENT_IDS);

    // when
    triggerEvent(SCOPE_KEY, 1, eventTrigger1, -1L);
    triggerEvent(SCOPE_KEY, 2, eventTrigger2, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger2);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();
  }

  @Test
  void shouldPollStartEventTrigger() {
    // given
    final EventTrigger eventTrigger1 = createEventTrigger();
    final EventTrigger eventTrigger2 = createEventTrigger();

    // when
    triggerStartEvent(SCOPE_KEY, 1, eventTrigger1, -1L);
    triggerStartEvent(SCOPE_KEY, 2, eventTrigger2, -1L);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger1);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isEqualTo(eventTrigger2);
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();
  }

  @Test
  void shouldStoreProcessInstanceKeyInEventTrigger() {
    // given
    final EventTrigger eventTrigger = createEventTrigger();
    final long processInstanceKey = 12345L;
    triggerStartEvent(SCOPE_KEY, 1, eventTrigger, processInstanceKey);

    // when
    final EventTrigger stateEventTrigger = state.peekEventTrigger(SCOPE_KEY);

    // then
    assertThat(stateEventTrigger.getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldDeleteTrigger() {
    // given
    final long eventKey = 456;

    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);
    triggerEvent(SCOPE_KEY, eventKey, createEventTrigger(), -1L);

    // when
    state.deleteTrigger(SCOPE_KEY, eventKey);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();
  }

  @Test
  void shouldDeleteStartEventTrigger() {
    // given
    final EventTrigger eventTrigger1 = createEventTrigger();
    triggerStartEvent(SCOPE_KEY, 1, eventTrigger1, -1L);

    // when
    state.deleteTrigger(SCOPE_KEY, 1);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();
  }

  @Test
  void shouldDeleteEventScopeAndTriggers() {
    // given
    state.createInstance(SCOPE_KEY, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);
    triggerEvent(SCOPE_KEY, 1, createEventTrigger(), -1L);
    triggerEvent(SCOPE_KEY, 2, createEventTrigger(), -1L);
    triggerEvent(SCOPE_KEY, 3, createEventTrigger(), -1L);

    // when
    state.deleteInstance(SCOPE_KEY);

    // then
    assertThat(state.getInstance(SCOPE_KEY)).isNull();
    assertThat(state.peekEventTrigger(SCOPE_KEY)).isNull();
  }

  @Test
  void shouldDeleteStartEventTriggerOnDeletionOfInstance() {
    // given
    final EventTrigger eventTrigger1 = createEventTrigger();
    triggerStartEvent(SCOPE_KEY, 1, eventTrigger1, -1L);

    // when
    state.deleteInstance(SCOPE_KEY);

    // then
    assertThat(state.pollEventTrigger(SCOPE_KEY)).isNull();
  }

  @Test
  void shouldNotFailOnDeletionOfNonExistingInstance() {
    assertThatNoException().isThrownBy(() -> state.deleteInstance(SCOPE_KEY));
  }

  @Test
  void shouldResetEventScopeAfterCreation() {
    // given
    final long firstKey = 123;
    final long secondKey = 345;
    final var interruptingElementId = wrapString("interrupt");
    final var interruptingElementIds = Set.of(interruptingElementId);
    final var boundaryElementId = wrapString("boundary");
    final var boundaryElementIds = Set.of(boundaryElementId);

    // when
    state.createInstance(firstKey, interruptingElementIds, boundaryElementIds);
    state.createInstance(secondKey, NO_INTERRUPTING_ELEMENT_IDS, NO_BOUNDARY_ELEMENT_IDS);

    // then
    final var firstEventScopeInstance = state.getInstance(firstKey);
    assertThat(firstEventScopeInstance.isInterruptingElementId(interruptingElementId)).isTrue();
    assertThat(firstEventScopeInstance.isBoundaryElementId(boundaryElementId)).isTrue();

    final var secondEventScopeInstance = state.getInstance(secondKey);
    assertThat(secondEventScopeInstance.isInterruptingElementId(interruptingElementId)).isFalse();
    assertThat(secondEventScopeInstance.isBoundaryElementId(boundaryElementId)).isFalse();
  }

  private void triggerEvent(
      final long eventScopeKey,
      final long eventKey,
      final EventTrigger eventTrigger,
      final long processInstanceKey) {
    state.triggerEvent(
        eventScopeKey,
        eventKey,
        eventTrigger.getElementId(),
        eventTrigger.getVariables(),
        processInstanceKey);
  }

  private void triggerStartEvent(
      final long eventScopeKey,
      final long eventKey,
      final EventTrigger eventTrigger,
      final long processInstanceKey) {
    state.triggerStartEvent(
        eventScopeKey,
        eventKey,
        eventTrigger.getElementId(),
        eventTrigger.getVariables(),
        processInstanceKey);
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
