/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import io.camunda.zeebe.engine.processing.scheduled.api.Sink;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

/** Test-only TaskContext + Sink that records emitted commands and inter-partition sends. */
public final class FakeTaskContext implements TaskContext, Sink {

  private final List<AppendedCommand> appended = new ArrayList<>();
  private final List<InterPartitionSend> sends = new ArrayList<>();
  private InstantSource clock = InstantSource.fixed(Instant.ofEpochMilli(1_000_000L));
  private boolean shouldYield = false;
  private int partitionId = 1;
  private int batchCapacity = Integer.MAX_VALUE;

  public static FakeTaskContext create() {
    return new FakeTaskContext();
  }

  public FakeTaskContext withClockMillis(final long millis) {
    clock = InstantSource.fixed(Instant.ofEpochMilli(millis));
    return this;
  }

  public FakeTaskContext withShouldYield(final boolean shouldYield) {
    this.shouldYield = shouldYield;
    return this;
  }

  public FakeTaskContext withBatchCapacity(final int capacity) {
    batchCapacity = capacity;
    return this;
  }

  public List<AppendedCommand> appended() {
    return appended;
  }

  public List<InterPartitionSend> sends() {
    return sends;
  }

  @Override
  public InstantSource clock() {
    return clock;
  }

  @Override
  public Sink sink() {
    return this;
  }

  @Override
  public boolean shouldYield() {
    return shouldYield;
  }

  @Override
  public int partitionId() {
    return partitionId;
  }

  @Override
  public boolean append(final Intent intent, final UnifiedRecordValue value) {
    return append(-1L, intent, value);
  }

  @Override
  public boolean append(final long key, final Intent intent, final UnifiedRecordValue value) {
    if (appended.size() >= batchCapacity) {
      return false;
    }
    appended.add(new AppendedCommand(key, intent, value));
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
    sends.add(new InterPartitionSend(receiverPartitionId, valueType, intent, recordKey, value));
  }

  public record AppendedCommand(long key, Intent intent, UnifiedRecordValue value) {}

  public record InterPartitionSend(
      int receiverPartitionId,
      ValueType valueType,
      Intent intent,
      Long recordKey,
      UnifiedRecordValue value) {}
}
