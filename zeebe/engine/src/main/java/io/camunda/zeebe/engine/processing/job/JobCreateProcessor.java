/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processes the {@link JobIntent#CREATE} command: creates a <em>standalone</em> job — a job with no
 * owning process instance — and, when the caller awaits the result, remembers the request so the
 * result can be delivered synchronously on completion (see {@link JobResolveAwaitResultProcessor}).
 *
 * <p>The created job flows through the exact same activation/completion machinery as a BPMN job;
 * the only differences are that no process/element context is set and the job is marked with {@link
 * JobKind#STANDALONE}.
 */
public final class JobCreateProcessor implements TypedRecordProcessor<JobRecord> {

  private static final String ERROR_BLANK_TYPE =
      "Expected to create a standalone job with a non-empty type, but no type was provided";
  private static final String ERROR_INVALID_RETRIES =
      "Expected to create a standalone job with retries greater than 0, but got '%d'";

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final JobProcessingMetrics jobMetrics;

  public JobCreateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final AsyncRequestBehavior asyncRequestBehavior,
      final JobProcessingMetrics jobMetrics) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.jobActivationBehavior = jobActivationBehavior;
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.jobMetrics = jobMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final JobRecord job = command.getValue();

    if (job.getType().isBlank()) {
      rejectAndRespond(command, RejectionType.INVALID_ARGUMENT, ERROR_BLANK_TYPE);
      return;
    }
    if (job.getRetries() <= 0) {
      rejectAndRespond(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_INVALID_RETRIES.formatted(job.getRetries()));
      return;
    }

    // A standalone job has no owning process instance. Mark it and clear any process/element
    // context a client may have supplied, so it can never masquerade as a BPMN job.
    job.setJobKind(JobKind.STANDALONE)
        .setProcessInstanceKey(-1L)
        .setProcessDefinitionKey(-1L)
        .setElementInstanceKey(-1L)
        .setBpmnProcessId("")
        .setElementId("");

    final long jobKey = keyGenerator.nextKey();

    // Remember the awaiting caller (if any) so the result can be delivered on completion. The
    // behavior does not guard on request metadata itself, so a fire-and-forget create must not
    // register a request (it would leak, since it would never be processed).
    if (command.hasRequestMetadata()) {
      asyncRequestBehavior.writeAsyncRequestReceived(jobKey, command);
    }

    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CREATED, job);
    jobActivationBehavior.publishWork(jobKey, job);
    jobMetrics.countJobEvent(JobAction.CREATED, JobKind.STANDALONE, job.getType());
  }

  private void rejectAndRespond(
      final TypedRecord<JobRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectionOnCommand(command, type, reason);
  }
}
