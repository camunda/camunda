/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.StringUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@code ProcessingResultBuilder} that buffers the processing results. After
 * being done with processing the build can be turned into a immutable {@link ProcessingResult},
 * which allows to process the result further.
 */
final class BufferedProcessingResultBuilder implements ProcessingResultBuilder {

  private final List<PostCommitTask> postCommitTasks = new ArrayList<>();

  private final RecordBatch mutableRecordBatch;
  private ProcessingResponseImpl processingResponse;
  private final long operationReference;

  BufferedProcessingResultBuilder(final RecordBatchSizePredicate predicate) {
    this(predicate, operationReferenceNullValue());
  }

  BufferedProcessingResultBuilder(
      final RecordBatchSizePredicate predicate, final long operationReference) {
    mutableRecordBatch = new RecordBatch(predicate);
    this.operationReference = operationReference;
  }

  @Override
  public Either<RuntimeException, ProcessingResultBuilder> appendRecordReturnEither(
      final long key, final RecordValue value, final RecordMetadata metadata) {

    if (operationReference != operationReferenceNullValue()) {
      metadata.operationReference(operationReference);
    }

    final ValueType valueType = TypedEventRegistry.TYPE_REGISTRY.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new IllegalStateException("Missing value type mapping for record: " + value.getClass());
    }

    if (value instanceof final UnifiedRecordValue unifiedRecordValue) {
      final var metadataWithValueType = metadata.valueType(valueType);
      final var either =
          mutableRecordBatch.appendRecord(key, metadataWithValueType, -1, unifiedRecordValue);
      if (either.isLeft()) {
        return Either.left(either.getLeft());
      }
    } else {
      throw new IllegalStateException(
          String.format(
              "The record value %s is not a UnifiedRecordValue",
              StringUtil.limitString(value.toString(), 1024)));
    }

    return Either.right(this);
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
    if (requestId == RecordMetadataEncoder.requestIdNullValue()) {
      return this;
    }
    final var metadata =
        new RecordMetadata()
            .recordType(recordType)
            .intent(intent)
            .rejectionType(rejectionType)
            .rejectionReason(rejectionReason)
            .valueType(valueType)
            .operationReference(operationReference);
    final var entry = RecordBatchEntry.createEntry(key, metadata, -1, value);
    processingResponse = new ProcessingResponseImpl(entry, requestId, requestStreamId);
    return this;
  }

  @Override
  public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
    postCommitTasks.add(task);
    return this;
  }

  @Override
  public ProcessingResultBuilder resetPostCommitTasks() {
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResult build() {
    return new BufferedResult(mutableRecordBatch, processingResponse, postCommitTasks);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return mutableRecordBatch.canAppendRecordOfLength(eventLength);
  }

  record ProcessingResponseImpl(RecordBatchEntry responseValue, long requestId, int requestStreamId)
      implements ProcessingResponse {}
}
