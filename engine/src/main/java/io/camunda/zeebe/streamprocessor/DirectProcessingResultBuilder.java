/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.PostCommitTask;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

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
  private final TypedResponseWriter responseWriter;

  private boolean hasResponse =
      true; // TODO set to false after the process builder class is used by the engine

  DirectProcessingResultBuilder(final StreamProcessorContext context) {
    this.context = context;
    streamWriter = context.getLogStreamWriter();
    responseWriter = context.getTypedResponseWriter();
  }

  @Override
  public ProcessingResultBuilder appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value) {
    streamWriter.appendRecord(key, type, intent, rejectionType, rejectionReason, value);
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
    hasResponse = true;
    responseWriter.writeResponse(
        eventKey, eventState, eventValue, valueType, requestId, requestStreamId);
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
    return new DirectProcessingResult(context, postCommitTasks, hasResponse);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return streamWriter.canWriteEventOfLength(eventLength);
  }

  @Override
  public int getMaxEventLength() {
    return streamWriter.getMaxEventLength();
  }
}
