/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/** RocksDB implementation of the {@link EventScopeStore}. */
public final class DbEventScopeStore implements EventScopeStore {

  private final DbLong eventScopeKey;
  private final EventScopeInstance eventScopeInstance;
  private final ColumnFamily<DbLong, EventScopeInstance> eventScopeInstanceColumnFamily;

  private final DbLong eventTriggerScopeKey;
  private final DbLong eventTriggerEventKey;
  private final DbCompositeKey<DbLong, DbLong> eventTriggerKey;
  private final EventTrigger eventTrigger;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, EventTrigger> eventTriggerColumnFamily;

  public DbEventScopeStore(
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
  public EventScopeInstance getScopeInstance(final long scopeKey) {
    eventScopeKey.wrapLong(scopeKey);
    return eventScopeInstanceColumnFamily.get(eventScopeKey);
  }

  @Override
  public void insertScopeInstance(final long scopeKey, final EventScopeInstance instance) {
    eventScopeKey.wrapLong(scopeKey);
    eventScopeInstanceColumnFamily.insert(eventScopeKey, instance);
  }

  @Override
  public void updateScopeInstance(final long scopeKey, final EventScopeInstance instance) {
    eventScopeKey.wrapLong(scopeKey);
    eventScopeInstanceColumnFamily.update(eventScopeKey, instance);
  }

  @Override
  public void deleteScopeInstance(final long scopeKey) {
    eventScopeKey.wrapLong(scopeKey);
    eventScopeInstanceColumnFamily.deleteIfExists(eventScopeKey);
  }

  @Override
  public void insertTrigger(
      final long scopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    eventTriggerScopeKey.wrapLong(scopeKey);
    eventTriggerEventKey.wrapLong(eventKey);

    eventTrigger
        .setElementId(elementId)
        .setVariables(variables)
        .setEventKey(eventKey)
        .setProcessInstanceKey(processInstanceKey);

    eventTriggerColumnFamily.insert(eventTriggerKey, eventTrigger);
  }

  @Override
  public void deleteTrigger(final long scopeKey, final long eventKey) {
    eventTriggerScopeKey.wrapLong(scopeKey);
    eventTriggerEventKey.wrapLong(eventKey);
    eventTriggerColumnFamily.deleteIfExists(eventTriggerKey);
  }

  @Override
  public void deleteTriggers(final long scopeKey) {
    eventTriggerScopeKey.wrapLong(scopeKey);
    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (DbCompositeKey<DbLong, DbLong> key) -> eventTriggerColumnFamily.deleteIfExists(key));
  }

  @Override
  public EventTrigger peekTrigger(final long scopeKey) {
    eventTriggerScopeKey.wrapLong(scopeKey);
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
  public void visitTriggers(final long scopeKey, final Predicate<EventTrigger> visitor) {
    eventTriggerScopeKey.wrapLong(scopeKey);
    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (key, value) -> {
          return visitor.test(value);
        });
  }
}
