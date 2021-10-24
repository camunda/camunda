/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import java.util.Collection;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;

public final class DbEventScopeInstanceState implements MutableEventScopeInstanceState {

  private final DbLong eventScopeKey;
  private final EventScopeInstance eventScopeInstance;
  private final ColumnFamily<DbLong, EventScopeInstance> eventScopeInstanceColumnFamily;

  private final DbLong eventTriggerScopeKey;
  private final DbLong eventTriggerEventKey;
  private final DbCompositeKey<DbLong, DbLong> eventTriggerKey;
  private final EventTrigger eventTrigger;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, EventTrigger> eventTriggerColumnFamily;

  public DbEventScopeInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
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
  }

  @Override
  public void shutdownInstance(final long eventScopeKey) {
    final EventScopeInstance instance = getInstance(eventScopeKey);
    if (instance != null) {
      this.eventScopeKey.wrapLong(eventScopeKey);
      instance.setAccepting(false);
      eventScopeInstanceColumnFamily.put(this.eventScopeKey, instance);
    }
  }

  @Override
  public synchronized boolean createIfNotExists(
      final long eventScopeKey, final Collection<DirectBuffer> interruptingIds) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    boolean wasCreated = false;

    if (!eventScopeInstanceColumnFamily.exists(this.eventScopeKey)) {
      createInstance(eventScopeKey, interruptingIds);
      wasCreated = true;
    }

    return wasCreated;
  }

  @Override
  public synchronized void createInstance(
      final long eventScopeKey, final Collection<DirectBuffer> interruptingIds) {
    eventScopeInstance.reset();

    this.eventScopeKey.wrapLong(eventScopeKey);
    eventScopeInstance.setAccepting(true);
    for (final DirectBuffer interruptingId : interruptingIds) {
      eventScopeInstance.addInterrupting(interruptingId);
    }

    eventScopeInstanceColumnFamily.put(this.eventScopeKey, eventScopeInstance);
  }

  @Override
  public synchronized void deleteInstance(final long eventScopeKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);

    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (BiConsumer<DbCompositeKey<DbLong, DbLong>, EventTrigger>)
            (key, value) -> deleteTrigger(key));

    this.eventScopeKey.wrapLong(eventScopeKey);
    eventScopeInstanceColumnFamily.delete(this.eventScopeKey);
  }

  @Override
  public synchronized EventTrigger pollEventTrigger(final long eventScopeKey) {
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
  public synchronized boolean triggerEvent(
      final long eventScopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(this.eventScopeKey);

    if (isAcceptingEvent(instance)) {
      if (instance.isInterrupting(elementId)) {
        instance.setAccepting(false);
        eventScopeInstanceColumnFamily.put(this.eventScopeKey, instance);
      }

      createTrigger(eventScopeKey, eventKey, elementId, variables);

      return true;
    } else {
      return false;
    }
  }

  @Override
  public synchronized void triggerStartEvent(
      final long processDefinitionKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables) {
    createTrigger(processDefinitionKey, eventKey, elementId, variables);
  }

  @Override
  public synchronized void deleteTrigger(final long eventScopeKey, final long eventKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    eventTriggerEventKey.wrapLong(eventKey);
    deleteTrigger(eventTriggerKey);
  }

  @Override
  public synchronized EventScopeInstance getInstance(final long eventScopeKey) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(this.eventScopeKey);
    return instance != null ? new EventScopeInstance(instance) : null;
  }

  @Override
  public synchronized EventTrigger peekEventTrigger(final long eventScopeKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    final EventTrigger[] next = new EventTrigger[1];
    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (key, value) -> {
          next[0] = new EventTrigger(value);
          return false;
        });

    return next[0];
  }

  @Override
  public synchronized boolean isAcceptingEvent(final long eventScopeKey) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(this.eventScopeKey);

    return isAcceptingEvent(instance);
  }

  private boolean isAcceptingEvent(final EventScopeInstance instance) {
    return instance != null && instance.isAccepting();
  }

  private void createTrigger(
      final long eventScopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    eventTriggerEventKey.wrapLong(eventKey);

    eventTrigger.setElementId(elementId).setVariables(variables).setEventKey(eventKey);

    eventTriggerColumnFamily.put(eventTriggerKey, eventTrigger);
  }

  private void deleteTrigger(final DbCompositeKey<DbLong, DbLong> triggerKey) {
    eventTriggerColumnFamily.delete(triggerKey);
  }
}
