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
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class DbTimerInstanceState implements MutableTimerInstanceState {

  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbLong>, TimerInstance>
      timerInstanceColumnFamily;
  private final DbLong timerKey;
  private final DbForeignKey<DbLong> elementInstanceKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> elementAndTimerKey;

  private final ColumnFamily<
          DbCompositeKey<DbLong, DbCompositeKey<DbForeignKey<DbLong>, DbLong>>, DbNil>
      dueDateColumnFamily;
  private final DbLong dueDate;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbForeignKey<DbLong>, DbLong>>
      dueDateCompositeKey;

  // (processInstanceKey, elementInstanceKey, timerKey) -> nil, populated for held timers only. The
  // key holds only fields that are immutable for the life of a timer, so no update path can
  // desync it; the BPMN element id, which migration does change, is read back off the stored timer
  // instead of being part of the key.
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbLong>>, DbNil>
      heldTimerByProcessInstanceColumnFamily;
  private final DbLong heldProcessInstanceKey;
  private final DbLong heldElementInstanceKey;
  private final DbLong heldTimerKey;
  private final DbCompositeKey<DbLong, DbLong> heldElementAndTimerKey;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbLong>>
      heldTimerByProcessInstanceKey;

  private long nextDueDate;

  public DbTimerInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    final TimerInstance timerInstance = new TimerInstance();
    timerKey = new DbLong();
    elementInstanceKey =
        new DbForeignKey<>(
            new DbLong(),
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
            MatchType.Full,
            (k) -> k.getValue() == -1);
    elementAndTimerKey = new DbCompositeKey<>(elementInstanceKey, timerKey);
    timerInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TIMERS, transactionContext, elementAndTimerKey, timerInstance);

    dueDate = new DbLong();
    dueDateCompositeKey = new DbCompositeKey<>(dueDate, elementAndTimerKey);
    dueDateColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TIMER_DUE_DATES,
            transactionContext,
            dueDateCompositeKey,
            DbNil.INSTANCE);

    heldProcessInstanceKey = new DbLong();
    heldElementInstanceKey = new DbLong();
    heldTimerKey = new DbLong();
    heldElementAndTimerKey = new DbCompositeKey<>(heldElementInstanceKey, heldTimerKey);
    heldTimerByProcessInstanceKey =
        new DbCompositeKey<>(heldProcessInstanceKey, heldElementAndTimerKey);
    heldTimerByProcessInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.HELD_TIMER_BY_PROCESS_INSTANCE,
            transactionContext,
            heldTimerByProcessInstanceKey,
            DbNil.INSTANCE);
  }

  @Override
  public void store(final TimerInstance timer) {
    timerKey.wrapLong(timer.getKey());
    elementInstanceKey.inner().wrapLong(timer.getElementInstanceKey());

    timerInstanceColumnFamily.insert(elementAndTimerKey, timer);

    if (timer.isHeld()) {
      // a held timer is kept out of the due-date index so the scheduler never fires it; it fires
      // only when triggered explicitly. Instead it is indexed by its process instance so a public
      // trigger command that addresses it by (processInstanceKey, elementId) can enumerate the
      // instance's held timers and match the element id against the stored timer.
      wrapHeldKey(timer);
      heldTimerByProcessInstanceColumnFamily.insert(heldTimerByProcessInstanceKey, DbNil.INSTANCE);
      return;
    }

    dueDate.wrapLong(timer.getDueDate());
    dueDateColumnFamily.insert(dueDateCompositeKey, DbNil.INSTANCE);
  }

  @Override
  public void remove(final TimerInstance timer) {
    elementInstanceKey.inner().wrapLong(timer.getElementInstanceKey());
    timerKey.wrapLong(timer.getKey());
    timerInstanceColumnFamily.deleteExisting(elementAndTimerKey);

    dueDate.wrapLong(timer.getDueDate());
    dueDateColumnFamily.deleteIfExists(dueDateCompositeKey);

    wrapHeldKey(timer);
    heldTimerByProcessInstanceColumnFamily.deleteIfExists(heldTimerByProcessInstanceKey);
  }

  private void wrapHeldKey(final TimerInstance timer) {
    heldProcessInstanceKey.wrapLong(timer.getProcessInstanceKey());
    heldElementInstanceKey.wrapLong(timer.getElementInstanceKey());
    heldTimerKey.wrapLong(timer.getKey());
  }

  @Override
  public void update(final TimerInstance timer) {
    elementInstanceKey.inner().wrapLong(timer.getElementInstanceKey());
    timerKey.wrapLong(timer.getKey());
    timerInstanceColumnFamily.update(elementAndTimerKey, timer);
  }

  @Override
  public void suspend(final long elementInstanceKey, final long timerKey, final long dueDate) {
    wrapDueDateKey(elementInstanceKey, timerKey, dueDate);
    dueDateColumnFamily.deleteIfExists(dueDateCompositeKey);
  }

  @Override
  public void resume(final long elementInstanceKey, final long timerKey, final long dueDate) {
    final var timer = get(elementInstanceKey, timerKey);
    // a held timer was never in the due-date index to begin with, so resuming must not add it —
    // otherwise suspending and resuming an instance would hand its held timers to the scheduler.
    if (timer != null && !timer.isHeld()) {
      wrapDueDateKey(elementInstanceKey, timerKey, dueDate);
      dueDateColumnFamily.upsert(dueDateCompositeKey, DbNil.INSTANCE);
    }
  }

  private void wrapDueDateKey(
      final long elementInstanceKey, final long timerKey, final long dueDate) {
    this.elementInstanceKey.inner().wrapLong(elementInstanceKey);
    this.timerKey.wrapLong(timerKey);
    this.dueDate.wrapLong(dueDate);
  }

  @Override
  public long processTimersWithDueDateBefore(final long timestamp, final TimerVisitor consumer) {
    nextDueDate = -1L;

    dueDateColumnFamily.whileTrue(
        key -> {
          final var dueDate = key.first().getValue();
          final var elementAndTimerKey = key.second();

          boolean consumed = false;
          if (dueDate <= timestamp) {
            final var timerInstance = timerInstanceColumnFamily.get(elementAndTimerKey);
            if (timerInstance == null) {
              // Time for due date no longer exists. This can occur due to the following data race:
              // 1. Scheduled task reads a due date for a timer
              // 2. Processing removes timer and due date
              // 3. Scheduled task fails to find timer
              // Because timer and due date were already removed, we can ignore this here.
              return true;
            }
            consumed = consumer.visit(timerInstance);
          }

          if (!consumed) {
            nextDueDate = dueDate;
          }
          return consumed;
        });

    return nextDueDate;
  }

  @Override
  public void forEachTimerForElementInstance(
      final long elementInstanceKey, final Consumer<TimerInstance> action) {
    this.elementInstanceKey.inner().wrapLong(elementInstanceKey);

    timerInstanceColumnFamily.whileEqualPrefix(
        this.elementInstanceKey,
        (key, value) -> {
          action.accept(value);
        });
  }

  @Override
  public TimerInstance get(final long elementInstanceKey, final long timerKey) {
    this.elementInstanceKey.inner().wrapLong(elementInstanceKey);
    this.timerKey.wrapLong(timerKey);

    return timerInstanceColumnFamily.get(elementAndTimerKey);
  }

  @Override
  public HeldTimerResolution resolveHeldByProcessElement(
      final long processInstanceKey, final DirectBuffer elementId) {
    heldProcessInstanceKey.wrapLong(processInstanceKey);

    final List<long[]> heldKeys = new ArrayList<>();
    heldTimerByProcessInstanceColumnFamily.whileEqualPrefix(
        heldProcessInstanceKey,
        (key, value) -> {
          final var elementAndTimer = key.second();
          heldKeys.add(
              new long[] {elementAndTimer.first().getValue(), elementAndTimer.second().getValue()});
        });

    long[] match = null;
    for (final long[] heldKey : heldKeys) {
      final var timer = get(heldKey[0], heldKey[1]);
      if (timer == null || !BufferUtil.equals(elementId, timer.getHandlerNodeId())) {
        continue;
      }
      if (match != null) {
        return HeldTimerResolution.ambiguous();
      }
      match = heldKey;
    }

    if (match == null) {
      return HeldTimerResolution.notFound();
    }
    return HeldTimerResolution.found(get(match[0], match[1]));
  }
}
