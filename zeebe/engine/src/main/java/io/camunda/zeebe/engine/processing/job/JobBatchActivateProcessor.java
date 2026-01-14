/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.job.JobBatchCollector.TooLargeJob;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.Collections;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class JobBatchActivateProcessor implements TypedRecordProcessor<JobBatchRecord> {

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final JobBatchCollector jobBatchCollector;
  private final KeyGenerator keyGenerator;
  private final JobProcessingMetrics jobMetrics;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public JobBatchActivateProcessor(
      final Writers writers,
      final ProcessingState state,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final InstantSource clock) {

    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    jobBatchCollector =
        new JobBatchCollector(
            state.getJobState(),
            state.getVariableState(),
            stateWriter::canWriteEventOfLength,
            clock);

    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
  }

  @Override
  public void processRecord(final TypedRecord<JobBatchRecord> record) {
    final JobBatchRecord value = record.getValue();
    if (isValid(value)) {
      activateJobs(record);
    } else {
      rejectCommand(record);
    }
  }

  private boolean isValid(final JobBatchRecord record) {
    return record.getMaxJobsToActivate() > 0
        && record.getTimeout() > 0
        && record.getTypeBuffer().capacity() > 0;
  }

  private void activateJobs(final TypedRecord<JobBatchRecord> record) {
    final JobBatchRecord value = record.getValue();
    final long jobBatchKey = keyGenerator.nextKey();

    final Either<TooLargeJob, Map<JobKind, Integer>> result = jobBatchCollector.collectJobs(record);
    final var activatedJobCountPerJobKind = result.getOrElse(Collections.emptyMap());
    result.ifLeft(
        largeJob ->
            raiseIncidentJobTooLargeForMessageSize(
                largeJob.key(), largeJob.jobRecord(), largeJob.expectedEventLength()));

    activateJobBatch(record, value, jobBatchKey, activatedJobCountPerJobKind);
  }

  private void rejectCommand(final TypedRecord<JobBatchRecord> record) {
    final RejectionType rejectionType;
    final String rejectionReason;
    final JobBatchRecord value = record.getValue();
    final var format = "Expected to activate job batch with %s to be %s, but it was %s";

    if (value.getMaxJobsToActivate() < 1) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              format,
              "max jobs to activate",
              "greater than zero",
              String.format("'%d'", value.getMaxJobsToActivate()));
    } else if (value.getTimeout() < 1) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              format, "timeout", "greater than zero", String.format("'%d'", value.getTimeout()));
    } else if (value.getTypeBuffer().capacity() < 1) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = String.format(format, "type", "present", "blank");
    } else {
      throw new IllegalStateException(
          "Expected to reject an invalid activate job batch command, but it appears to be valid");
    }

    rejectionWriter.appendRejection(record, rejectionType, rejectionReason);
    responseWriter.writeRejectionOnCommand(record, rejectionType, rejectionReason);
  }

  private void activateJobBatch(
      final TypedRecord<JobBatchRecord> record,
      final JobBatchRecord value,
      final long jobBatchKey,
      final Map<JobKind, Integer> activatedJobsCountPerJobKind) {
    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    responseWriter.writeEventOnCommand(jobBatchKey, JobBatchIntent.ACTIVATED, value, record);
    activatedJobsCountPerJobKind.forEach(
        (jobKind, count) ->
            jobMetrics.countJobEvent(JobAction.ACTIVATED, jobKind, value.getType(), count));
  }

  private void raiseIncidentJobTooLargeForMessageSize(
      final long jobKey, final JobRecord job, final int expectedJobRecordSize) {
    final String jobSize = ByteValue.prettyPrint(expectedJobRecordSize);
    final DirectBuffer incidentMessage =
        wrapString(
            String.format(
                "The job with key '%s' can not be activated, because with %s it is larger than the configured message size (per default is 4 MB). "
                    + "Try to reduce the size by reducing the number of fetched variables or modifying the variable values.",
                jobKey, jobSize));

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceState(elementInstanceState)
            .withProcessState(processState)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    final var incidentEvent =
        new IncidentRecord()
            .setErrorType(ErrorType.MESSAGE_SIZE_EXCEEDED)
            .setErrorMessage(incidentMessage)
            .setBpmnProcessId(job.getBpmnProcessIdBuffer())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setElementId(job.getElementIdBuffer())
            .setElementInstanceKey(job.getElementInstanceKey())
            .setJobKey(jobKey)
            .setTenantId(job.getTenantId())
            .setVariableScopeKey(job.getElementInstanceKey())
            .setElementInstancePath(treePathProperties.elementInstancePath())
            .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
            .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
  }
}
