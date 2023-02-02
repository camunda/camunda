/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record MockProcessingResult(List<Event> records) implements ProcessingResult {

  @Override
  public ImmutableRecordBatch getRecordBatch() {
    return RecordBatch.empty();
  }

  @Override
  public Optional<ProcessingResponse> getProcessingResponse() {
    return Optional.empty();
  }

  @Override
  public boolean executePostCommitTasks() {
    return false;
  }

  @Override
  public boolean isEmpty() {
    return records().isEmpty();
  }

  record Event(
      Intent intent,
      RecordType type,
      RejectionType rejectionType,
      String rejectionReason,
      long key,
      RecordValue value) {}

  static class MockProcessingResultBuilder implements ProcessingResultBuilder {

    final List<Event> followupRecords = new ArrayList<>();

    @Override
    public Either<RuntimeException, ProcessingResultBuilder> appendRecordReturnEither(
        final long key,
        final RecordType type,
        final Intent intent,
        final RejectionType rejectionType,
        final String rejectionReason,
        final RecordValue value) {

      final var record = new Event(intent, type, rejectionType, rejectionReason, key, value);
      followupRecords.add(record);
      return Either.right(null);
    }

    @Override
    public ProcessingResultBuilder withResponse(
        final RecordType type,
        final long key,
        final Intent intent,
        final UnpackedObject value,
        final ValueType valueType,
        final RejectionType rejectionType,
        final String rejectionReason,
        final long requestId,
        final int requestStreamId) {
      return null;
    }

    @Override
    public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
      return null;
    }

    @Override
    public ProcessingResultBuilder resetPostCommitTasks() {
      return null;
    }

    @Override
    public ProcessingResult build() {
      return new MockProcessingResult(followupRecords);
    }

    @Override
    public boolean canWriteEventOfLength(final int eventLength) {
      return false;
    }
  }
}
