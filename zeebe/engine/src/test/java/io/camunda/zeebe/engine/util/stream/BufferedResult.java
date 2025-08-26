/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.stream;

import io.camunda.zeebe.engine.util.stream.TestBufferedProcessingResultBuilder.ProcessingResponseImpl;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link ProcessingResult} and {@link TaskResult} that buffers the processing and
 * taks results, which will then be written to logstream or send as response.
 */
final class BufferedResult implements ProcessingResult, TaskResult {

  private final List<PostCommitTask> postCommitTasks;
  private final ImmutableRecordBatch immutableRecordBatch;
  private final ProcessingResponseImpl processingResponse;
  private final boolean processInASeparateBatch;

  BufferedResult(
      final ImmutableRecordBatch immutableRecordBatch,
      final ProcessingResponseImpl processingResponse,
      final List<PostCommitTask> postCommitTasks,
      final boolean processInASeparateBatch) {
    this.postCommitTasks = new ArrayList<>(postCommitTasks);
    this.processingResponse = processingResponse;
    this.immutableRecordBatch = immutableRecordBatch;
    this.processInASeparateBatch = processInASeparateBatch;
  }

  @Override
  public ImmutableRecordBatch getRecordBatch() {
    return immutableRecordBatch;
  }

  @Override
  public Optional<ProcessingResponse> getProcessingResponse() {
    return Optional.ofNullable(processingResponse);
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

  @Override
  public boolean isEmpty() {
    return getProcessingResponse().isEmpty()
        && getRecordBatch().isEmpty()
        && postCommitTasks.isEmpty();
  }

  @Override
  public boolean shouldProcessInASeparateBatch() {
    return processInASeparateBatch;
  }
}
