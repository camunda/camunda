/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/** The event scope domain rules, over an {@link EventScopeStore}. */
public final class EventScopeInstanceStateImpl implements MutableEventScopeInstanceState {

  private final EventScopeStore store;
  private final EventScopeInstance newEventScopeInstance = new EventScopeInstance();

  public EventScopeInstanceStateImpl(final EventScopeStore store) {
    this.store = store;
  }

  @Override
  public void createInstance(
      final long eventScopeKey,
      final Collection<DirectBuffer> interruptingElementIds,
      final Collection<DirectBuffer> boundaryElementIds) {
    newEventScopeInstance.reset();

    newEventScopeInstance.setAccepting(true);
    newEventScopeInstance.setInterrupted(false);

    for (final DirectBuffer elementId : interruptingElementIds) {
      newEventScopeInstance.addInterruptingElementId(elementId);
    }
    for (final DirectBuffer elementId : boundaryElementIds) {
      newEventScopeInstance.addBoundaryElementId(elementId);
    }

    store.insertScopeInstance(eventScopeKey, newEventScopeInstance);
  }

  @Override
  public void deleteInstance(final long eventScopeKey) {
    store.deleteTriggers(eventScopeKey);
    store.deleteScopeInstance(eventScopeKey);
  }

  @Override
  public EventTrigger pollEventTrigger(final long eventScopeKey) {
    final EventTrigger next = store.peekTrigger(eventScopeKey);
    if (next != null) {
      store.deleteTrigger(eventScopeKey, next.getEventKey());
    }
    return next;
  }

  @Override
  public void triggerEvent(
      final long eventScopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    final EventScopeInstance instance = store.getScopeInstance(eventScopeKey);

    if (canTriggerEvent(instance, elementId)) {
      final var isInterruptingElementId = instance.isInterruptingElementId(elementId);
      final var isBoundaryElementId = instance.isBoundaryElementId(elementId);

      if (isInterruptingElementId) {
        instance.setInterrupted(true);
      }
      if (isBoundaryElementId && isInterruptingElementId) {
        // don't accept other events after an interrupting boundary is triggered
        instance.setAccepting(false);
      }
      store.updateScopeInstance(eventScopeKey, instance);

      store.insertTrigger(eventScopeKey, eventKey, elementId, variables, processInstanceKey);
    }
  }

  @Override
  public void triggerStartEvent(
      final long processDefinitionKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    store.insertStartEventTrigger(
        processDefinitionKey, eventKey, elementId, variables, processInstanceKey);
  }

  @Override
  public void deleteTrigger(final long eventScopeKey, final long eventKey) {
    store.deleteTrigger(eventScopeKey, eventKey);
  }

  @Override
  public EventScopeInstance getInstance(final long eventScopeKey) {
    final EventScopeInstance instance = store.getScopeInstance(eventScopeKey);
    return instance != null ? new EventScopeInstance(instance) : null;
  }

  @Override
  public EventTrigger peekEventTrigger(final long eventScopeKey) {
    return store.peekTrigger(eventScopeKey);
  }

  @Override
  public Optional<EventTrigger> findEventTrigger(
      final long eventScopeKey, final Predicate<EventTrigger> predicate) {
    final AtomicReference<EventTrigger> found = new AtomicReference<>();

    store.visitTriggers(
        eventScopeKey,
        trigger -> {
          if (predicate.test(trigger)) {
            final var copiedEventTrigger = new EventTrigger();
            copiedEventTrigger.copyFrom(trigger);
            found.set(copiedEventTrigger);
            return false;
          } else {
            return true;
          }
        });

    return Optional.ofNullable(found.get());
  }

  @Override
  public boolean canTriggerEvent(final long eventScopeKey, final DirectBuffer elementId) {
    return canTriggerEvent(store.getScopeInstance(eventScopeKey), elementId);
  }

  /**
   * An event scope can be triggered if no interrupting event was triggered (i.e. it is not
   * interrupted). If an interrupting event was triggered then no other event can be triggered,
   * except for boundary events. If an interrupting boundary event was triggered then no other
   * events, including boundary events, can be triggered (i.e. it is not accepting any events).
   */
  private boolean canTriggerEvent(final EventScopeInstance instance, final DirectBuffer elementId) {
    return instance != null
        && instance.isAccepting()
        && (!instance.isInterrupted() || instance.isBoundaryElementId(elementId));
  }
}
