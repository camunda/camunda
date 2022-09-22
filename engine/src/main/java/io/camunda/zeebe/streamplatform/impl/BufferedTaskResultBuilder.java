/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamplatform.impl;

import static io.camunda.zeebe.engine.processing.streamprocessor.TypedEventRegistry.TYPE_REGISTRY;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.streamplatform.api.TaskResult;
import io.camunda.zeebe.streamplatform.api.TaskResultBuilder;
import io.camunda.zeebe.streamplatform.api.records.MutableRecordBatch;
import io.camunda.zeebe.streamplatform.api.records.RecordBatch;
import io.camunda.zeebe.streamplatform.api.records.RecordBatchSizePredicate;

/**
 * Implementation of {@code TaskResultBuilder} that buffers the task results. After being done with
 * task execution the {@link #build()} will turn the result into a immutable {@link TaskResult},
 * which allows to process the result further.
 */
final class BufferedTaskResultBuilder implements TaskResultBuilder {

  private final MutableRecordBatch mutableRecordBatch;

  BufferedTaskResultBuilder(final RecordBatchSizePredicate predicate) {
    mutableRecordBatch = new RecordBatch(predicate);
  }

  @Override
  public boolean appendCommandRecord(
      final long key, final Intent intent, final UnifiedRecordValue value) {

    final ValueType valueType = TYPE_REGISTRY.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new IllegalStateException("Missing value type mapping for record: " + value.getClass());
    }

    final var either =
        mutableRecordBatch.appendRecord(
            key, -1, RecordType.COMMAND, intent, RejectionType.NULL_VAL, "", valueType, value);

    return either.isRight();
  }

  @Override
  public TaskResult build() {
    return () -> mutableRecordBatch;
  }
}
