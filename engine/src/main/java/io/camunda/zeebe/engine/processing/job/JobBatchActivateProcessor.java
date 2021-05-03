/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.VariableState;
import io.zeebe.engine.state.immutable.ZeebeState;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.ByteValue;
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
  private final long maxJobBatchLength;

  private final ObjectHashSet<DirectBuffer> variableNames = new ObjectHashSet<>();

  public JobBatchActivateProcessor(
      final Writers writers,
      final ZeebeState state,
      final KeyGenerator keyGenerator,
      final long maxRecordLength) {

    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();

    jobState = state.getJobState();
    variableState = state.getVariableState();
    this.keyGenerator = keyGenerator;

    this.maxRecordLength = maxRecordLength;
    // we can only add the half of the max record length to the job batch
    // because the jobs itself are also written to the same batch
    maxJobBatchLength = (maxRecordLength - Long.BYTES) / 2;
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

          if (remainingAmount >= 0
              && (record.getLength() + jobRecord.getLength()) <= maxJobBatchLength) {

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
