/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * The single output channel a {@link ScheduledTask} uses to emit work.
 *
 * <p>Two destinations are supported:
 *
 * <ul>
 *   <li>Local follow-up commands appended to this partition's log via {@link #append}. Subject to
 *       the result batch capacity — when {@link #append} returns {@code false} the batch is full
 *       and the task should stop iterating and return {@link Outcome.YieldNow}.
 *   <li>Inter-partition commands sent best-effort to other partitions via {@link
 *       #sendInterPartition}. Delivery is unreliable; receivers must be idempotent.
 * </ul>
 */
public interface Sink {

  /**
   * Appends a local command without an explicit record key.
   *
   * @return {@code true} if the record fit, {@code false} if the batch is full
   */
  boolean append(Intent intent, UnifiedRecordValue value);

  /**
   * Appends a local command with an explicit record key.
   *
   * @return {@code true} if the record fit, {@code false} if the batch is full
   */
  boolean append(long key, Intent intent, UnifiedRecordValue value);

  /** Sends a command to another partition. Best-effort, fire-and-forget. */
  void sendInterPartition(
      int receiverPartitionId,
      ValueType valueType,
      Intent intent,
      Long recordKey,
      UnifiedRecordValue value,
      AuthInfo authInfo);

  /** Convenience overload without {@link AuthInfo}. */
  default void sendInterPartition(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue value) {
    sendInterPartition(receiverPartitionId, valueType, intent, recordKey, value, null);
  }
}
