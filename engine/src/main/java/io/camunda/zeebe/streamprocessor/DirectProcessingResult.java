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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.retry.AbortableRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RetryStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Implementation of {@code ProcessingResult} that uses direct access to the stream and to response
 * writer. This implementation is here to support a bridge for legacy code. Legacy code can first be
 * shaped into the interfaces defined in engine abstraction, and subseqeently the interfaces can be
 * re-implemented to allow for buffered writing to stream and response writer
 */
final class DirectProcessingResult implements ProcessingResult {

  private final List<PostCommitTask> postCommitTasks;

  private final RetryStrategy writeRetryStrategy;
  private final BooleanSupplier abortCondition;
  private final TypedStreamWriter streamWriter;
  private final TypedResponseWriter responseWriter;
  private final boolean hasResponse;

  DirectProcessingResult(
      final StreamProcessorContext context,
      final List<PostCommitTask> postCommitTasks,
      final boolean hasResponse) {
    this.postCommitTasks = new ArrayList<>(postCommitTasks);
    writeRetryStrategy = new AbortableRetryStrategy(context.getActor());
    abortCondition = context.getAbortCondition();
    streamWriter = context.getLogStreamWriter();
    responseWriter = context.getTypedResponseWriter();

    this.hasResponse = hasResponse;
  }

  @Override
  public ActorFuture<Boolean> writeRecordsToStream(
      final LogStreamBatchWriter logStreamBatchWriter) {
    // here we must assume that stream writer is backed by log stream record writer internally
    return writeRetryStrategy.runWithRetry(
        () -> {
          final long position = streamWriter.flush();
          logStreamBatchWriter.tryWrite();
          return position >= 0;
        },
        abortCondition);
  }

  @Override
  public boolean writeResponse(final CommandResponseWriter commandResponseWriter) {
    // here we must assume that response writer is backed up by command response writer internally

    if (hasResponse) {
      return responseWriter.flush();
    } else {
      return true;
    }
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
