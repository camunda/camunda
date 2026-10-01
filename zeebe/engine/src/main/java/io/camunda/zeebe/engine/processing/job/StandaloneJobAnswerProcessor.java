/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState.AsyncRequest;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;

/**
 * Answers the client that created a standalone job with the answer of the worker.
 *
 * <p>The worker's complete, fail or throw error command is answered to the worker, and a processing
 * step has a single response. So that command appends an {@link JobIntent#ANSWER} command instead
 * (see {@link #appendAnswer}), whose processing answers the creator. The answer is the job as the
 * worker left it, and its outcome follows from it: an error code means the worker threw an error,
 * an error message without one that it failed, and neither that it completed.
 */
@ExcludeAuthorizationCheck
public final class StandaloneJobAnswerProcessor implements TypedRecordProcessor<JobRecord> {

  private final AsyncRequestState asyncRequestState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public StandaloneJobAnswerProcessor(final ProcessingState state, final Writers writers) {
    asyncRequestState = state.getAsyncRequestState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  /** Appends the answer of a worker for the standalone job, see the class documentation. */
  static void appendAnswer(
      final TypedCommandWriter commandWriter, final long jobKey, final JobRecord answer) {
    commandWriter.appendFollowUpCommand(jobKey, JobIntent.ANSWER, answer);
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    final var asyncRequest =
        command.isInternalCommand()
            ? asyncRequestState.findRequest(jobKey, ValueType.JOB, JobIntent.CREATE)
            : Optional.<AsyncRequest>empty();
    if (asyncRequest.isEmpty()) {
      // the job expired, and its creator got that answer, before the worker's answer was processed
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to answer the creator of standalone job with key '%d', but it was already"
                  .formatted(jobKey)
              + " answered");
      return;
    }

    final var request = asyncRequest.get();
    final JobRecord answer = command.getValue();
    responseWriter.writeAcceptedResponse(
        jobKey,
        outcomeOf(answer),
        answer,
        ValueType.JOB,
        request.requestId(),
        request.requestStreamId());
    stateWriter.appendFollowUpEvent(request.key(), AsyncRequestIntent.PROCESSED, request.record());
  }

  private static JobIntent outcomeOf(final JobRecord answer) {
    if (!answer.getErrorCode().isEmpty()) {
      return JobIntent.ERROR_THROWN;
    }
    if (!answer.getErrorMessage().isEmpty()) {
      return JobIntent.FAILED;
    }
    return JobIntent.COMPLETED;
  }
}
