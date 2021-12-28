/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.ByteValue;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobBatchActivateProcessor implements TypedRecordProcessor<JobBatchRecord> {

  private final StateWriter stateWriter;
  private final VariableState variableState;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  private final JobState jobState;
  private final KeyGenerator keyGenerator;
  private final long maxRecordLength;

  private final ObjectHashSet<DirectBuffer> variableNames = new ObjectHashSet<>();
  private final JobMetrics jobMetrics;

  public JobBatchActivateProcessor(
      final Writers writers,
      final ZeebeState state,
      final KeyGenerator keyGenerator,
      final long maxRecordLength,
      final JobMetrics jobMetrics) {

    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();

    jobState = state.getJobState();
    variableState = state.getVariableState();
    this.keyGenerator = keyGenerator;

    this.maxRecordLength = maxRecordLength;
    this.jobMetrics = jobMetrics;
  }

  @Override
  public void processRecord(
      final TypedRecord<JobBatchRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
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

    final AtomicInteger amount = new AtomicInteger(value.getMaxJobsToActivate());
    collectJobsToActivate(record, amount);

    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    responseWriter.writeEventOnCommand(jobBatchKey, JobBatchIntent.ACTIVATED, value, record);

    final var activatedJobsCount = record.getValue().getJobKeys().size();
    jobMetrics.jobActivated(value.getType(), activatedJobsCount);
  }

  private void collectJobsToActivate(
      final TypedRecord<JobBatchRecord> record, final AtomicInteger amount) {
    final JobBatchRecord value = record.getValue();
    final ValueArray<JobRecord> jobIterator = value.jobs();
    final ValueArray<LongValue> jobKeyIterator = value.jobKeys();

    // collect jobs for activation
    variableNames.clear();
    final ValueArray<StringValue> jobBatchVariables = value.variables();

    jobBatchVariables.forEach(
        v -> {
          final MutableDirectBuffer nameCopy = new UnsafeBuffer(new byte[v.getValue().capacity()]);
          nameCopy.putBytes(0, v.getValue(), 0, v.getValue().capacity());
          variableNames.add(nameCopy);
        });

    jobState.forEachActivatableJobs(
        value.getTypeBuffer(),
        (key, jobRecord) -> {
          int remainingAmount = amount.get();
          final long deadline = record.getTimestamp() + value.getTimeout();
          jobRecord.setDeadline(deadline).setWorker(value.getWorkerBuffer());

          // fetch and set variables, required here to already have the full size of the job record
          final long elementInstanceKey = jobRecord.getElementInstanceKey();
          if (elementInstanceKey >= 0) {
            final DirectBuffer variables = collectVariables(variableNames, elementInstanceKey);
            jobRecord.setVariables(variables);
          } else {
            jobRecord.setVariables(DocumentValue.EMPTY_DOCUMENT);
          }

          final int expectedBatchLength = estimateClaimedBatchLength(record, jobRecord);
          if (remainingAmount >= 0 && expectedBatchLength < maxRecordLength) {

            remainingAmount = amount.decrementAndGet();
            jobKeyIterator.add().setValue(key);
            final JobRecord arrayValueJob = jobIterator.add();

            // clone job record since buffer is reused during iteration
            final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(jobRecord.getLength());
            jobRecord.write(buffer, 0);
            arrayValueJob.wrap(buffer);
          } else {
            value.setTruncated(true);

            if (value.getJobs().isEmpty()) {
              raiseIncidentJobTooLargeForMessageSize(key, jobRecord);
            }

            return false;
          }

          return remainingAmount > 0;
        });
  }

  /**
   * Estimates the size of the batch that we will need to claim from the dispatcher when writing the
   * job batch activation record. This is based on knowledge of the batch writer's internals, and
   * the assumption that only ONE record is going to be written as part of this batch.
   *
   * <p>When only one record is written in a batch, its length is essentially the length of the
   * metadata (which, when not a rejection, is fixed) plus the length of the value, plus the fixed
   * log entry descriptor header size. This is framed, then aligned, and gives us an accurate
   * estimate.
   *
   * <p>Note in this case that the metadata length plus the value length is returned by calling
   * {@link TypedRecord#getLength()}.
   */
  private int estimateClaimedBatchLength(
      final TypedRecord<JobBatchRecord> record, final JobRecord jobRecord) {
    final var currentEstimatedLength = record.getLength() + LogEntryDescriptor.HEADER_BLOCK_LENGTH;
    final var estimatedLengthWithNewJob = currentEstimatedLength + jobRecord.getLength();

    return LogBufferAppender.claimedBatchLength(1, (int) estimatedLengthWithNewJob);
  }

  private DirectBuffer collectVariables(
      final Collection<DirectBuffer> variableNames, final long elementInstanceKey) {
    final DirectBuffer variables;
    if (variableNames.isEmpty()) {
      variables = variableState.getVariablesAsDocument(elementInstanceKey);
    } else {
      variables = variableState.getVariablesAsDocument(elementInstanceKey, variableNames);
    }
    return variables;
  }

  private void rejectCommand(final TypedRecord<JobBatchRecord> record) {
    final RejectionType rejectionType;
    final String rejectionReason;

    final JobBatchRecord value = record.getValue();

    final String format = "Expected to activate job batch with %s to be %s, but it was %s";

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

  private void raiseIncidentJobTooLargeForMessageSize(final long jobKey, final JobRecord job) {

    final String messageSize = ByteValue.prettyPrint(maxRecordLength);

    final DirectBuffer incidentMessage =
        wrapString(
            String.format(
                "The job with key '%s' can not be activated because it is larger than the configured message size (%s). "
                    + "Try to reduce the size by reducing the number of fetched variables or modifying the variable values.",
                jobKey, messageSize));

    final IncidentRecord incidentEvent =
        new IncidentRecord()
            .setErrorType(ErrorType.MESSAGE_SIZE_EXCEEDED)
            .setErrorMessage(incidentMessage)
            .setBpmnProcessId(job.getBpmnProcessIdBuffer())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setElementId(job.getElementIdBuffer())
            .setElementInstanceKey(job.getElementInstanceKey())
            .setJobKey(jobKey)
            .setVariableScopeKey(job.getElementInstanceKey());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
  }
}
