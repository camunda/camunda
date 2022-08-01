/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.engine.api.PostCommitTask;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

record MockProcessingResult(List<Event> records) implements ProcessingResult {

  @Override
  public long writeRecordsToStream(final LogStreamBatchWriter logStreamBatchWriter) {
    return 0;
  }

  @Override
  public boolean writeResponse(final CommandResponseWriter commandResponseWriter) {
    return false;
  }

  @Override
  public boolean executePostCommitTasks() {
    return false;
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
    public ProcessingResultBuilder appendRecord(
        final long key,
        final RecordType type,
        final Intent intent,
        final RejectionType rejectionType,
        final String rejectionReason,
        final RecordValue value) {

      final var record = new Event(intent, type, rejectionType, rejectionReason, key, value);
      followupRecords.add(record);
      return null;
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
    public ProcessingResultBuilder reset() {
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

    @Override
    public int getMaxEventLength() {
      return 0;
    }
  }
}
