/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.MutableReference;
import org.agrona.collections.ObjectHashSet;

/**
 * Collects jobs to be activated as part of a {@link JobBatchRecord}. Activate-able jobs are read
 * from the {@link JobState}, resolving and setting their variables from the {@link VariableState},
 * and added to the given batch record.
 */
final class JobBatchCollector {
  private final ObjectHashSet<DirectBuffer> variableNames = new ObjectHashSet<>();

  private final JobState jobState;
  private final JobVariablesCollector jobVariablesCollector;
  private final Predicate<Integer> canWriteEventOfLength;

  /**
   * @param jobState the state from which jobs are collected
   * @param variableState the state from which variables are resolved and collected
   * @param canWriteEventOfLength a predicate which should return whether the resulting {@link
   *     TypedRecord} containing the {@link JobBatchRecord} will be writable or not. The predicate
   *     takes in the size of the record, and should return true if it can write such a record, and
   *     false otherwise
   */
  JobBatchCollector(
      final JobState jobState,
      final VariableState variableState,
      final Predicate<Integer> canWriteEventOfLength) {
    this.jobState = jobState;
    this.canWriteEventOfLength = canWriteEventOfLength;
    jobVariablesCollector = new JobVariablesCollector(variableState);
  }

  /**
   * Collects jobs to be added to the given {@code record}. The jobs and their keys are added
   * directly to the given record.
   *
   * <p>This method will fail only if it could not activate anything because the batch would be too
   * large, but there was at least one job to activate. On failure, it will return that job and its
   * key. On success, it will return the amount of jobs activated.
   *
   * @param record the batch activate command; jobs and their keys will be added directly into it
   * @return the amount of activated jobs on success, or a job which was too large to activate
   */
  Either<TooLargeJob, Integer> collectJobs(final TypedRecord<JobBatchRecord> record) {
    final JobBatchRecord value = record.getValue();
    final ValueArray<JobRecord> jobIterator = value.jobs();
    final ValueArray<LongValue> jobKeyIterator = value.jobKeys();
    final Collection<DirectBuffer> requestedVariables = collectVariableNames(value);
    final var maxActivatedCount = value.getMaxJobsToActivate();
    final var activatedCount = new MutableInteger(0);
    final var jobCopyBuffer = new ExpandableArrayBuffer();
    final var unwritableJob = new MutableReference<TooLargeJob>();

    jobState.forEachActivatableJobs(
        value.getTypeBuffer(),
        (key, jobRecord) -> {
          // fill in the job record properties first in order to accurately estimate its size before
          // adding it to the batch
          final var deadline = record.getTimestamp() + value.getTimeout();
          jobRecord.setDeadline(deadline).setWorker(value.getWorkerBuffer());
          jobVariablesCollector.setJobVariables(
              requestedVariables, jobRecord, jobRecord.getElementInstanceKey());

          // the expected length is based on the current record's length plus the length of the job
          // record we would add to the batch, the number of bytes taken by the additional job key,
          // as well as one byte required per job key for its type header. if we ever add more, this
          // should be updated accordingly.
          final var jobRecordLength = jobRecord.getLength();
          final var expectedEventLength = record.getLength() + jobRecordLength + Long.BYTES + 1;
          if (activatedCount.value <= maxActivatedCount
              && canWriteEventOfLength.test(expectedEventLength)) {
            appendJobToBatch(jobIterator, jobKeyIterator, jobCopyBuffer, key, jobRecord);
            activatedCount.increment();
          } else {
            // if no jobs were activated, then the current job is simply too large, and we cannot
            // activate it
            if (activatedCount.value == 0) {
              unwritableJob.set(new TooLargeJob(key, jobRecord, expectedEventLength));
            }

            value.setTruncated(true);
            return false;
          }

          return activatedCount.value < maxActivatedCount;
        });

    if (unwritableJob.ref != null) {
      return Either.left(unwritableJob.ref);
    }

    return Either.right(activatedCount.value);
  }

  private void appendJobToBatch(
      final ValueArray<JobRecord> jobIterator,
      final ValueArray<LongValue> jobKeyIterator,
      final ExpandableArrayBuffer jobCopyBuffer,
      final Long key,
      final JobRecord jobRecord) {
    jobKeyIterator.add().setValue(key);
    final JobRecord arrayValueJob = jobIterator.add();

    // clone job record since buffer is reused during iteration
    jobRecord.write(jobCopyBuffer, 0);
    arrayValueJob.wrap(jobCopyBuffer, 0, jobRecord.getLength());
  }

  private Collection<DirectBuffer> collectVariableNames(final JobBatchRecord batchRecord) {
    final ValueArray<StringValue> requestedVariables = batchRecord.variables();

    variableNames.clear();
    requestedVariables.forEach(
        variable -> variableNames.add(BufferUtil.cloneBuffer(variable.getValue())));

    return variableNames;
  }

  record TooLargeJob(long key, JobRecord jobRecord, int expectedEventLength) {}
}
