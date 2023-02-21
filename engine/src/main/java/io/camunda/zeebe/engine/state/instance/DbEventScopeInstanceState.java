/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Collection;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;

public final class DbEventScopeInstanceState implements MutableEventScopeInstanceState {

  private final DbElementInstanceState elementInstanceState;

  private final DbLong eventScopeKey;
  private final EventScopeInstance eventScopeInstance;
  private final ColumnFamily<DbLong, EventScopeInstance> eventScopeInstanceColumnFamily;

  private final DbLong eventTriggerScopeKey;
  private final DbLong eventTriggerEventKey;
  private final DbCompositeKey<DbLong, DbLong> eventTriggerKey;
  private final EventTrigger eventTrigger;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, EventTrigger> eventTriggerColumnFamily;

  public DbEventScopeInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final ElementInstanceState elementInstanceState) {
    eventScopeKey = new DbLong();
    eventScopeInstance = new EventScopeInstance();
    eventScopeInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EVENT_SCOPE, transactionContext, eventScopeKey, eventScopeInstance);

    eventTriggerScopeKey = new DbLong();
    eventTriggerEventKey = new DbLong();
    eventTriggerKey = new DbCompositeKey<>(eventTriggerScopeKey, eventTriggerEventKey);
    eventTrigger = new EventTrigger();
    eventTriggerColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EVENT_TRIGGER, transactionContext, eventTriggerKey, eventTrigger);

    this.elementInstanceState = (DbElementInstanceState) elementInstanceState;
  }

  @Override
  public void createInstance(
      final long eventScopeKey,
      final Collection<DirectBuffer> interruptingElementIds,
      final Collection<DirectBuffer> boundaryElementIds) {
    eventScopeInstance.reset();

    this.eventScopeKey.wrapLong(eventScopeKey);
    eventScopeInstance.setAccepting(true);
    eventScopeInstance.setInterrupted(false);

    for (final DirectBuffer elementId : interruptingElementIds) {
      eventScopeInstance.addInterruptingElementId(elementId);
    }
    for (final DirectBuffer elementId : boundaryElementIds) {
      eventScopeInstance.addBoundaryElementId(elementId);
    }

    eventScopeInstanceColumnFamily.insert(this.eventScopeKey, eventScopeInstance);
  }

  @Override
  public void deleteInstance(final long eventScopeKey) {
    final var elementInstance = elementInstanceState.getInstance(eventScopeKey);
    final var eventTriggers = elementInstance.getEventTriggers();

    if (eventTriggers > 0) {
      final MutableInteger triggerCount = new MutableInteger(eventTriggers);
      eventTriggerScopeKey.wrapLong(eventScopeKey);
      eventTriggerColumnFamily.whileEqualPrefix(
          eventTriggerScopeKey,
          (KeyValuePairVisitor<DbCompositeKey<DbLong, DbLong>, EventTrigger>)
              (key, value) -> {
                deleteTrigger(key);
                return triggerCount.decrementAndGet() > 0;
              });
    }

    this.eventScopeKey.wrapLong(eventScopeKey);
    eventScopeInstanceColumnFamily.deleteIfExists(this.eventScopeKey);
  }

  @Override
  public EventTrigger pollEventTrigger(final long eventScopeKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    final EventTrigger[] next = new EventTrigger[1];
    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (key, value) -> {
          next[0] = new EventTrigger(value);
          deleteTrigger(key);
          return false;
        });

    return next[0];
  }

  @Override
  public void triggerEvent(
      final long eventScopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(this.eventScopeKey);

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
      eventScopeInstanceColumnFamily.update(this.eventScopeKey, instance);

      createTrigger(eventScopeKey, eventKey, elementId, variables, processInstanceKey);
    }
  }

  @Override
  public void triggerStartEvent(
      final long processDefinitionKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    createTrigger(processDefinitionKey, eventKey, elementId, variables, processInstanceKey);
  }

  @Override
  public void deleteTrigger(final long eventScopeKey, final long eventKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    eventTriggerEventKey.wrapLong(eventKey);
    deleteTrigger(eventTriggerKey);
  }

  @Override
  public EventScopeInstance getInstance(final long eventScopeKey) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(this.eventScopeKey);
    return instance != null ? new EventScopeInstance(instance) : null;
  }

  @Override
  public EventTrigger peekEventTrigger(final long eventScopeKey) {
    final var elementInstance = elementInstanceState.getInstance(eventScopeKey);

    final EventTrigger[] next = new EventTrigger[1];
    if (elementInstance != null && elementInstance.getEventTriggers() > 0) {
      eventTriggerScopeKey.wrapLong(eventScopeKey);
      eventTriggerColumnFamily.whileEqualPrefix(
          eventTriggerScopeKey,
          (key, value) -> {
            next[0] = new EventTrigger(value);
            return false;
          });
    }

    return next[0];
  }

  @Override
  public boolean canTriggerEvent(final long eventScopeKey, final DirectBuffer elementId) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(this.eventScopeKey);

    return canTriggerEvent(instance, elementId);
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

  private void createTrigger(
      final long eventScopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    eventTriggerEventKey.wrapLong(eventKey);

    eventTrigger
        .setElementId(elementId)
        .setVariables(variables)
        .setEventKey(eventKey)
        .setProcessInstanceKey(processInstanceKey);

    eventTriggerColumnFamily.insert(eventTriggerKey, eventTrigger);

    final var elementInstance = elementInstanceState.getInstance(eventScopeKey);
    elementInstance.incrementEventTriggers();
    elementInstanceState.updateInstance(elementInstance);
  }

  private void deleteTrigger(final DbCompositeKey<DbLong, DbLong> triggerKey) {
    eventTriggerColumnFamily.deleteIfExists(triggerKey);
  }
}
