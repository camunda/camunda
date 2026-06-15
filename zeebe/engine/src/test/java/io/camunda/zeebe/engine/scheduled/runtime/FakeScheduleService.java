/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic, in-memory {@link ProcessingScheduleService} for tests. Tasks scheduled via {@code
 * runAt} fire when the caller advances the clock past their timestamp via {@link #advanceTo(long)}.
 * Cancellation is honored.
 */
public final class FakeScheduleService implements ProcessingScheduleService {

  private final FakeClock clock;
  private final PriorityQueue<Scheduled> queue =
      new PriorityQueue<>(Comparator.comparingLong(s -> s.fireAt));
  private final AtomicInteger seq = new AtomicInteger(0);
  private final List<String> firedNames = new ArrayList<>();
  private int syncCount;
  private int asyncCount;

  public FakeScheduleService(final FakeClock clock) {
    this.clock = clock;
  }

  public List<String> firedNames() {
    return firedNames;
  }

  public int syncCount() {
    return syncCount;
  }

  public int asyncCount() {
    return asyncCount;
  }

  /** Fires every queued task whose fire time is <= {@code timestamp}, in order. */
  public void advanceTo(final long timestamp) {
    clock.setNow(timestamp);
    Scheduled head;
    while ((head = queue.peek()) != null && head.fireAt <= timestamp) {
      queue.poll();
      if (head.cancelled) {
        continue;
      }
      firedNames.add("#" + head.id);
      if (head.task != null) {
        head.task.execute(new NoopTaskResultBuilder());
      } else if (head.runnable != null) {
        head.runnable.run();
      }
    }
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    syncCount++;
    return enqueue(clock.millis() + delay.toMillis(), null, task);
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Task task) {
    syncCount++;
    return enqueue(clock.millis() + delay.toMillis(), task, null);
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Task task) {
    syncCount++;
    return enqueue(timestamp, task, null);
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Runnable task) {
    syncCount++;
    return enqueue(timestamp, null, task);
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    syncCount++;
    enqueue(clock.millis() + delay.toMillis(), task, null);
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    asyncCount++;
    runAtFixedRate(delay, task);
  }

  @Override
  public ScheduledTask runDelayedAsync(final Duration delay, final Task task) {
    asyncCount++;
    return enqueue(clock.millis() + delay.toMillis(), task, null);
  }

  @Override
  public ScheduledTask runAtAsync(final long timestamp, final Task task) {
    asyncCount++;
    return enqueue(timestamp, task, null);
  }

  @Override
  public void runAtFixedRateAsync(
      final Duration delay, final Task task, final AsyncTaskGroup taskGroup) {
    asyncCount++;
    runAtFixedRate(delay, task);
  }

  @Override
  public ScheduledTask runDelayedAsync(
      final Duration delay, final Task task, final AsyncTaskGroup taskGroup) {
    asyncCount++;
    return enqueue(clock.millis() + delay.toMillis(), task, null);
  }

  @Override
  public ScheduledTask runAtAsync(
      final long timestamp, final Task task, final AsyncTaskGroup taskGroup) {
    asyncCount++;
    return enqueue(timestamp, task, null);
  }

  private ScheduledTask enqueue(final long fireAt, final Task task, final Runnable runnable) {
    final var scheduled = new Scheduled(seq.incrementAndGet(), fireAt, task, runnable);
    queue.add(scheduled);
    return () -> scheduled.cancelled = true;
  }

  static final class NoopTaskResultBuilder implements TaskResultBuilder {

    private static final ImmutableRecordBatch EMPTY_BATCH =
        new ImmutableRecordBatch() {
          @Override
          public List<LogAppendEntry> entries() {
            return Collections.emptyList();
          }

          @Override
          public Iterator<RecordBatchEntry> iterator() {
            return Collections.emptyIterator();
          }
        };

    private static final TaskResult EMPTY_RESULT = () -> EMPTY_BATCH;

    @Override
    public boolean appendCommandRecord(
        final long key,
        final Intent intent,
        final UnifiedRecordValue value,
        final FollowUpCommandMetadata metadata) {
      return true;
    }

    @Override
    public boolean canAppendRecords(
        final List<? extends UnifiedRecordValue> value, final FollowUpCommandMetadata metadata) {
      return true;
    }

    @Override
    public TaskResult build() {
      return EMPTY_RESULT;
    }
  }

  private static final class Scheduled {
    final int id;
    final long fireAt;
    final Task task;
    final Runnable runnable;
    volatile boolean cancelled;

    Scheduled(final int id, final long fireAt, final Task task, final Runnable runnable) {
      this.id = id;
      this.fireAt = fireAt;
      this.task = task;
      this.runnable = runnable;
    }
  }
}
