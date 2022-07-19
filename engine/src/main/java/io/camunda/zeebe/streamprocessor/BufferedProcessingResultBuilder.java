/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

/** Implementation of {@code ProcessingResultBuilder} that writes all data into a buffer */
final class BufferedProcessingResultBuilder implements ProcessingResultBuilder {

  private final BufferedStreamWriter bufferedStreamWriter;
  private final List<Runnable> postCommitTasks = new ArrayList<>();

  BufferedProcessingResultBuilder(final int maxFragmentSize) {
    bufferedStreamWriter = new BufferedStreamWriter(maxFragmentSize);
  }

  @Override
  public ProcessingResultBuilder appendRecord(
      final long key,
      final int sourceIndex,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value) {
    bufferedStreamWriter.appendRecord(
        key, sourceIndex, type, intent, rejectionType, rejectionReason, value);
    return this;
  }

  @Override
  public ProcessingResultBuilder withResponse(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final ValueType valueType,
      final long requestId,
      final int requestStreamId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public ProcessingResultBuilder appendPostCommitTask(final Runnable r) {
    postCommitTasks.add(r);
    return this;
  }

  @Override
  public ProcessingResultBuilder reset() {
    bufferedStreamWriter.reset();
    postCommitTasks.clear();
    return this;
  }

  @Override
  public ProcessingResult build() {
    throw new RuntimeException("Not yet implemented");
  }
}
