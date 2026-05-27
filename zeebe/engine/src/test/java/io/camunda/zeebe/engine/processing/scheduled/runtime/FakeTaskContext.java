/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.AppendedCommand;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.InterPartitionSend;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only TaskContext that records appended commands, inter-partition sends, and the scheduling
 * decision. Inspect the captured {@link Result} via {@link #lastResult()} after the task returns.
 */
public final class FakeTaskContext<C> implements TaskContext<C> {

  private InstantSource clock = InstantSource.fixed(Instant.ofEpochMilli(1_000_000L));
  private boolean shouldYield = false;
  private int partitionId = 1;
  private int batchCapacity = Integer.MAX_VALUE;
  private C resumeCursor = null;

  private final FakeBuilder<C> builder = new FakeBuilder<>();

  /** Convenience for the common {@code <Void>} case. */
  public static FakeTaskContext<Void> create() {
    return new FakeTaskContext<>();
  }

  /** Use when the task declares a non-{@code Void} cursor type. */
  public static <C> FakeTaskContext<C> createFor(final Class<C> cursorType) {
    return new FakeTaskContext<>();
  }

  public FakeTaskContext<C> withClockMillis(final long millis) {
    clock = InstantSource.fixed(Instant.ofEpochMilli(millis));
    return this;
  }

  public FakeTaskContext<C> withShouldYield(final boolean shouldYield) {
    this.shouldYield = shouldYield;
    return this;
  }

  public FakeTaskContext<C> withBatchCapacity(final int capacity) {
    batchCapacity = capacity;
    return this;
  }

  public FakeTaskContext<C> withResumeCursor(final C cursor) {
    resumeCursor = cursor;
    return this;
  }

  /**
   * The {@link Result} reflecting the most recent terminal call on this context's builder plus all
   * commands and sends recorded so far. Returns {@code null} if the task has not yet called a
   * terminal. Tests use this to assert on appended commands, inter-partition sends, and the
   * scheduling {@link Decision}; commands appended via captured visitors after the task returns are
   * visible here.
   */
  public Result lastResult() {
    return builder.lastDecision == null
        ? null
        : new Result(builder.appendedCommands, builder.interPartitionSends, builder.lastDecision);
  }

  @Override
  public InstantSource clock() {
    return clock;
  }

  @Override
  public int partitionId() {
    return partitionId;
  }

  @Override
  public boolean shouldYield() {
    return shouldYield;
  }

  @Override
  public C resumeCursor() {
    return resumeCursor;
  }

  @Override
  public Result.Builder<C> result() {
    return builder;
  }

  /** Tiny in-memory Builder. Records appended commands subject to {@code batchCapacity}. */
  private final class FakeBuilder<X> implements Result.Builder<X> {

    private final List<AppendedCommand> appendedCommands = new ArrayList<>();
    private final List<InterPartitionSend> interPartitionSends = new ArrayList<>();
    private Decision lastDecision;

    @Override
    public boolean append(final Intent intent, final UnifiedRecordValue value) {
      return append(-1L, intent, value);
    }

    @Override
    public boolean append(final long key, final Intent intent, final UnifiedRecordValue value) {
      if (appendedCommands.size() >= batchCapacity) {
        return false;
      }
      appendedCommands.add(new AppendedCommand(key, intent, value));
      return true;
    }

    @Override
    public void sendInterPartition(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue value,
        final AuthInfo authInfo) {
      interPartitionSends.add(
          new InterPartitionSend(
              receiverPartitionId, valueType, intent, recordKey, value, authInfo));
    }

    @Override
    public Result idle() {
      return capture(Decision.Idle.INSTANCE);
    }

    @Override
    public Result awaitDueAt(final long timestampMs) {
      return capture(new Decision.AwaitDueAt(timestampMs));
    }

    @Override
    public Result yieldNow(final X cursor) {
      return capture(new Decision.YieldNow(cursor));
    }

    private Result capture(final Decision decision) {
      lastDecision = decision;
      return new Result(appendedCommands, interPartitionSends, decision);
    }
  }
}
