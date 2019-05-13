/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;

public class JobBatchActivateProcessor implements TypedRecordProcessor<JobBatchRecord> {

  private final JobState jobState;
  private final VariablesState variablesState;
  private final KeyGenerator keyGenerator;
  private final ObjectHashSet<DirectBuffer> variableNames = new ObjectHashSet<>();

  public JobBatchActivateProcessor(
      JobState jobState, VariablesState variablesState, KeyGenerator keyGenerator) {
    this.jobState = jobState;
    this.variablesState = variablesState;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(
      final TypedRecord<JobBatchRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final JobBatchRecord value = record.getValue();
    if (isValid(value)) {
      activateJobs(record, responseWriter, streamWriter);
    } else {
      rejectCommand(record, responseWriter, streamWriter);
    }
  }

  private boolean isValid(final JobBatchRecord record) {
    return record.getMaxJobsToActivate() > 0
        && record.getTimeout() > 0
        && record.getType().capacity() > 0
        && record.getWorker().capacity() > 0;
  }

  private void activateJobs(
      final TypedRecord<JobBatchRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final JobBatchRecord value = record.getValue();

    final long jobBatchKey = keyGenerator.nextKey();

    final AtomicInteger amount = new AtomicInteger(value.getMaxJobsToActivate());
    collectJobsToActivate(record, amount);

    // Collecting of jobs and update state and write ACTIVATED job events should be separate,
    // since otherwise this will cause some problems (weird behavior) with the reusing of objects
    //
    // ArrayProperty.add will give always the same object - state.activate will
    // set/use this object for writing the new job state
    activateJobs(streamWriter, value);

    streamWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    responseWriter.writeEventOnCommand(jobBatchKey, JobBatchIntent.ACTIVATED, value, record);
  }

  private void collectJobsToActivate(TypedRecord<JobBatchRecord> record, AtomicInteger amount) {
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
        value.getType(),
        (key, jobRecord) -> {
          int remainingAmount = amount.get();
          final long deadline = currentTimeMillis() + value.getTimeout();
          jobRecord.setDeadline(deadline).setWorker(value.getWorker());

          // fetch and set variables, required here to already have the full size of the job record
          final long elementInstanceKey = jobRecord.getHeaders().getElementInstanceKey();
          if (elementInstanceKey >= 0) {
            final DirectBuffer variables = collectVariables(variableNames, elementInstanceKey);
            jobRecord.setVariables(variables);
          } else {
            jobRecord.setVariables(DocumentValue.EMPTY_DOCUMENT);
          }

          if (remainingAmount >= 0
              && value.getLength() + Long.BYTES + jobRecord.getLength()
                  <= record.getMaxValueLength()) {
            remainingAmount = amount.decrementAndGet();
            jobKeyIterator.add().setValue(key);
            final JobRecord arrayValueJob = jobIterator.add();

            // clone job record since buffer is reused during iteration
            final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(jobRecord.getLength());
            jobRecord.write(buffer, 0);
            arrayValueJob.wrap(buffer);
          } else {
            value.setTruncated(true);
            return false;
          }

          return remainingAmount > 0;
        });
  }

  private void activateJobs(TypedStreamWriter streamWriter, JobBatchRecord value) {
    final Iterator<JobRecord> iterator = value.jobs().iterator();
    final Iterator<LongValue> keyIt = value.jobKeys().iterator();
    while (iterator.hasNext() && keyIt.hasNext()) {
      final JobRecord jobRecord = iterator.next();
      final LongValue next1 = keyIt.next();
      final long key = next1.getValue();

      // update state and write follow up event for job record
      // we have to copy the job record because #write will reset the iterator state
      final ExpandableArrayBuffer copy = new ExpandableArrayBuffer();
      jobRecord.write(copy, 0);
      final JobRecord copiedJob = new JobRecord();
      copiedJob.wrap(copy, 0, jobRecord.getLength());

      // first write follow up event as state.activate will clear the variables
      streamWriter.appendFollowUpEvent(key, JobIntent.ACTIVATED, copiedJob);
      jobState.activate(key, copiedJob);
    }
  }

  private DirectBuffer collectVariables(
      Collection<DirectBuffer> variableNames, long elementInstanceKey) {
    final DirectBuffer variables;
    if (variableNames.isEmpty()) {
      variables = variablesState.getVariablesAsDocument(elementInstanceKey);
    } else {
      variables = variablesState.getVariablesAsDocument(elementInstanceKey, variableNames);
    }
    return variables;
  }

  private void rejectCommand(
      final TypedRecord<JobBatchRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
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
    } else if (value.getType().capacity() < 1) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = String.format(format, "type", "present", "blank");
    } else if (value.getWorker().capacity() < 1) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = String.format(format, "worker", "present", "blank");
    } else {
      throw new IllegalStateException(
          "Expected to reject an invalid activate job batch command, but it appears to be valid");
    }

    streamWriter.appendRejection(record, rejectionType, rejectionReason);
    responseWriter.writeRejectionOnCommand(record, rejectionType, rejectionReason);
  }
}
