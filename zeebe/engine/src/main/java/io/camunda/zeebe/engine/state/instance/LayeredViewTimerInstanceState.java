/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.ViewPublisher;
import io.camunda.zeebe.db.layered.typed.LayeredViewReader;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link TimerInstanceState} for the asynchronous due-date checker over the layered state store's
 * read views (experimental; only wired when the layered-state flag is on): each {@link
 * #processTimersWithDueDateBefore} acquires the latest published {@link ReadOnlyView}, scans it,
 * and releases it — so the scan observes a consistent, slightly stale cut of the committed state
 * instead of reading a separate transaction context that would miss the buffered writes. Key
 * encoding mirrors {@link DbTimerInstanceState} exactly; both read the same column families.
 *
 * <p>Only the due-date scan is supported — it is the only read the checker performs. The other
 * {@link TimerInstanceState} reads belong to processors, which read the owner state.
 *
 * <p><b>Threading:</b> one instance per reader; the flyweights are shared across calls, so calls
 * must not overlap (the checker executes on a single actor, which guarantees exactly that).
 */
public final class LayeredViewTimerInstanceState implements TimerInstanceState {

  private final ViewPublisher views;

  private final TimerInstance timerInstance = new TimerInstance();
  private final DbLong timerKey = new DbLong();
  private final DbForeignKey<DbLong> elementInstanceKey =
      new DbForeignKey<>(
          new DbLong(),
          ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
          MatchType.Full,
          k -> k.getValue() == -1);
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> elementAndTimerKey =
      new DbCompositeKey<>(elementInstanceKey, timerKey);
  private final DbLong dueDate = new DbLong();
  private final DbCompositeKey<DbLong, DbCompositeKey<DbForeignKey<DbLong>, DbLong>>
      dueDateCompositeKey = new DbCompositeKey<>(dueDate, elementAndTimerKey);

  private long nextDueDate;

  public LayeredViewTimerInstanceState(final ViewPublisher views) {
    this.views = Objects.requireNonNull(views, "views");
  }

  @Override
  public long processTimersWithDueDateBefore(final long timestamp, final TimerVisitor consumer) {
    final ReadOnlyView view = views.acquireLatest();
    try {
      final var dueDates =
          new LayeredViewReader<>(
              view, ZbColumnFamilies.TIMER_DUE_DATES.name(), dueDateCompositeKey, DbNil.INSTANCE);
      final var timers =
          new LayeredViewReader<>(
              view, ZbColumnFamilies.TIMERS.name(), elementAndTimerKey, timerInstance);
      nextDueDate = -1L;

      dueDates.whileTrue(
          (key, nil) -> {
            final var dueDate = key.first().getValue();
            final var elementAndTimerKey = key.second();

            boolean consumed = false;
            if (dueDate <= timestamp) {
              final var timerInstance = timers.get(elementAndTimerKey);
              // unlike the owner path, a view is a consistent cut, so a due date without its timer
              // cannot be observed mid-removal; tolerate it anyway, mirroring DbTimerInstanceState
              if (timerInstance == null) {
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
    } finally {
      views.release(view);
    }
  }

  @Override
  public void forEachTimerForElementInstance(
      final long elementInstanceKey, final Consumer<TimerInstance> action) {
    throw new UnsupportedOperationException(
        "expected only due-date scans on the layered view timer state, but"
            + " forEachTimerForElementInstance was called");
  }

  @Override
  public TimerInstance get(final long elementInstanceKey, final long timerKey) {
    throw new UnsupportedOperationException(
        "expected only due-date scans on the layered view timer state, but get was called");
  }
}
