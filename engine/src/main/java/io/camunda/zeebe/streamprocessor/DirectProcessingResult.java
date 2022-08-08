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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@code ProcessingResult} that uses direct access to the stream and to response
 * writer. This implementation is here to support a bridge for legacy code. Legacy code can first be
 * shaped into the interfaces defined in engine abstraction, and subseqeently the interfaces can be
 * re-implemented to allow for buffered writing to stream and response writer
 */
final class DirectProcessingResult implements ProcessingResult {

  private final List<PostCommitTask> postCommitTasks;

  private final LegacyTypedStreamWriter streamWriter;
  private final DirectTypedResponseWriter responseWriter;
  private boolean hasResponse;

  DirectProcessingResult(
      final StreamProcessorContext context,
      final List<PostCommitTask> postCommitTasks,
      final boolean hasResponse) {
    this.postCommitTasks = new ArrayList<>(postCommitTasks);
    streamWriter = context.getLogStreamWriter();
    responseWriter = context.getTypedResponseWriter();

    this.hasResponse = hasResponse;
  }

  @Override
  public long writeRecordsToStream(final LogStreamBatchWriter logStreamBatchWriter) {
    return streamWriter.flush();
  }

  @Override
  public boolean writeResponse(final CommandResponseWriter commandResponseWriter) {
    // here we must assume that response writer is backed up by command response writer internally

    if (hasResponse) {
      hasResponse = false;
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
