/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.PostCommitTask;
import io.camunda.zeebe.engine.api.ProcessingResponse;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.streamprocessor.DirectProcessingResultBuilder.ProcessingResponseImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@code ProcessingResult} that uses direct access to the stream and to response
 * writer. This implementation is here to support a bridge for legacy code. Legacy code can first be
 * shaped into the interfaces defined in engine abstraction, and subseqeently the interfaces can be
 * re-implemented to allow for buffered writing to stream and response writer
 */
final class DirectProcessingResult implements ProcessingResult, TaskResult {

  private final List<PostCommitTask> postCommitTasks;
  private final ImmutableRecordBatch immutableRecordBatch;
  private final ProcessingResponseImpl processingResponse;

  DirectProcessingResult(
      final ImmutableRecordBatch immutableRecordBatch,
      final ProcessingResponseImpl processingResponse,
      final List<PostCommitTask> postCommitTasks) {
    this.postCommitTasks = new ArrayList<>(postCommitTasks);
    this.processingResponse = processingResponse;
    this.immutableRecordBatch = immutableRecordBatch;
  }

  @Override
  public ImmutableRecordBatch getRecordBatch() {
    return immutableRecordBatch;
  }

  @Override
  public Optional<ProcessingResponse> getProcessingResponse() {
    return Optional.of(processingResponse);
  }

  @Override
  public boolean executePostCommitTasks() {
    boolean aggregatedResult = true;

    for (final PostCommitTask task : postCommitTasks) {
      try {
        aggregatedResult = aggregatedResult && task.flush();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    return aggregatedResult;
  }
}
