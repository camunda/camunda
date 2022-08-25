/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.camunda.zeebe.engine.api.PostCommitTask;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.records.RecordBatch;
import io.camunda.zeebe.engine.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@code ProcessingResultBuilder} that uses direct access to the stream and to
 * response writer. This implementation is here to support a bridge for legacy code. Legacy code can
 * first be shaped into the interfaces defined in engine abstraction, and subseqeently the
 * interfaces can be re-implemented to allow for buffered writing to stream and response writer
 */
final class DirectProcessingResultBuilder implements ProcessingResultBuilder {

  private final List<PostCommitTask> postCommitTasks = new ArrayList<>();

  private final StreamProcessorContext context;
  private final LegacyTypedStreamWriter streamWriter;
  private final DirectTypedResponseWriter responseWriter;

  private boolean hasResponse =
      true; // TODO figure out why this still needs to be true for tests to pass
  private final long sourceRecordPosition;
  private final RecordBatch mutableRecordBatch;
  private final Map<Class<? extends UnpackedObject>, ValueType> typeRegistry;

  DirectProcessingResultBuilder(
      final StreamProcessorContext context,
      final long sourceRecordPosition,
      final RecordBatchSizePredicate predicate) {
    this.context = context;
    this.sourceRecordPosition = sourceRecordPosition;
    streamWriter = context.getLogStreamWriter();
    streamWriter.configureSourceContext(sourceRecordPosition);
    responseWriter = context.getTypedResponseWriter();
    mutableRecordBatch = new RecordBatch(predicate);
    typeRegistry = new HashMap<>();
    EVENT_REGISTRY.forEach((e, c) -> typeRegistry.put(c, e));
  }

  @Override
  public ProcessingResultBuilder appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value) {

    final ValueType valueType = typeRegistry.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new IllegalStateException("Missing value type mapping for record: " + value.getClass());
    }

    if (value instanceof UnifiedRecordValue unifiedRecordValue) {
      mutableRecordBatch.appendRecord(
          key, -1, type, intent, rejectionType, rejectionReason, valueType, unifiedRecordValue);
    } else {
      throw new IllegalStateException(
          String.format("The record value %s is not a UnifiedRecordValue", value));
    }

    streamWriter.appendRecord(key, type, intent, rejectionType, rejectionReason, value);
    return this;
  }

  @Override
  public ProcessingResultBuilder withResponse(
      final RecordType recordType,
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final ValueType valueType,
      final RejectionType rejectionType,
      final String rejectionReason,
      final long requestId,
      final int requestStreamId) {
    hasResponse = true;
    responseWriter.writeResponse(
        recordType,
        key,
        intent,
        value,
        valueType,
        rejectionType,
        rejectionReason,
        requestId,
        requestStreamId);
    return this;
  }

  @Override
  public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
    postCommitTasks.add(task);
    return this;
  }

  @Override
  public ProcessingResultBuilder reset() {
    streamWriter.reset();
    streamWriter.configureSourceContext(sourceRecordPosition);
    responseWriter.reset();
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResultBuilder resetPostCommitTasks() {
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResult build() {
    return new DirectProcessingResult(context, mutableRecordBatch, postCommitTasks, hasResponse);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return mutableRecordBatch.canAppendRecordOfLength(eventLength);
  }

  @Override
  public int getMaxEventLength() {
    return streamWriter.getMaxEventLength();
  }
}
