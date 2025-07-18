/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.records.MutableRecordBatch;
import io.camunda.zeebe.stream.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StagedScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import java.util.List;

/**
 * Implementation of {@code TaskResultBuilder} that buffers the task results. After being done with
 * task execution the {@link #build()} will turn the result into a immutable {@link TaskResult},
 * which allows to process the result further.
 */
public final class BufferedTaskResultBuilder implements TaskResultBuilder {

  private final MutableRecordBatch mutableRecordBatch;
  private final StagedScheduledCommandCache cache;

  public BufferedTaskResultBuilder(
      final RecordBatchSizePredicate predicate, final StagedScheduledCommandCache cache) {
    mutableRecordBatch = new RecordBatch(predicate);
    this.cache = cache;
  }

  @Override
  public boolean appendCommandRecord(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final FollowUpCommandMetadata metadata) {
    final ValueType valueType = getValueType(value);

    if (cache.contains(intent, key)) {
      return true;
    }

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .valueType(valueType)
            .operationReference(metadata.operationReference())
            .batchOperationReference(metadata.batchOperationReference());
    final var either = mutableRecordBatch.appendRecord(key, recordMetadata, -1, value);

    either.ifRight(ok -> cache.add(intent, key));
    return either.isRight();
  }

  @Override
  public boolean canAppendCommandRecords(
      final long key,
      final Intent intent,
      final List<? extends UnifiedRecordValue> values,
      final FollowUpCommandMetadata metadata) {
    if (values.isEmpty()) {
      return true;
    }

    if (values.stream().map(UnifiedRecordValue::getClass).distinct().count() > 1) {
      throw new IllegalArgumentException(
          "Cannot append multiple record types in a single batch: " + values);
    }

    final ValueType valueType = getValueType(values.getFirst());

    final var universalRecordMetadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .valueType(valueType)
            .operationReference(metadata.operationReference())
            .batchOperationReference(metadata.batchOperationReference());

    final var totalRecordLength =
        values.stream()
            .map(v -> RecordBatchEntry.createEntry(key, universalRecordMetadata, -1, v))
            .map(RecordBatchEntry::getLength)
            .reduce(0, Integer::sum);

    return mutableRecordBatch.canAppendRecordOfLength(totalRecordLength);
  }

  @Override
  public TaskResult build() {
    return () -> mutableRecordBatch;
  }

  private static ValueType getValueType(final UnifiedRecordValue values) {
    final ValueType valueType = TypedEventRegistry.TYPE_REGISTRY.get(values.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new IllegalStateException(
          "Missing value type mapping for record: " + values.getClass());
    }
    return valueType;
  }
}
