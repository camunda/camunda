/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Processes the engine-internal {@link JobIntent#RESOLVE_AWAIT_RESULT} follow-up command, appended
 * by {@link JobCompleteProcessor} once a standalone job that a caller is awaiting has completed.
 *
 * <p>It delivers the completed job (with its result variables) back to the original creator over
 * the {@code CREATE} command's request channel, then marks the awaited request as processed so it
 * is removed from state. This is a separate processing cycle because the worker's completion
 * response already occupied the {@code CompleteJob} cycle's single response slot.
 */
public final class JobResolveAwaitResultProcessor implements TypedRecordProcessor<JobRecord> {

  private final AsyncRequestState asyncRequestState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public JobResolveAwaitResultProcessor(final ProcessingState state, final Writers writers) {
    asyncRequestState = state.getAsyncRequestState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    asyncRequestState
        .findRequest(jobKey, ValueType.JOB, JobIntent.CREATE)
        .ifPresentOrElse(
            request -> {
              responseWriter.writeResponse(
                  jobKey,
                  JobIntent.COMPLETED,
                  command.getValue(),
                  ValueType.JOB,
                  request.requestId(),
                  request.requestStreamId());
              stateWriter.appendFollowUpEvent(
                  request.key(), AsyncRequestIntent.PROCESSED, request.record());
            },
            () ->
                rejectionWriter.appendRejection(
                    command,
                    RejectionType.NOT_FOUND,
                    "Expected to resolve the awaited result for standalone job with key '%d', but no awaiting request exists"
                        .formatted(jobKey)));
  }
}
