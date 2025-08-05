/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
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
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final Predicate<Integer> canWriteEventOfLength;

  /**
   * @param canWriteEventOfLength a predicate which should return whether the resulting {@link
   *     TypedRecord} containing the {@link JobBatchRecord} will be writable or not. The predicate
   *     takes in the size of the record, and should return true if it can write such a record, and
   *     false otherwise
   */
  JobBatchCollector(
      final ProcessingState state,
      final Predicate<Integer> canWriteEventOfLength,
      final AuthorizationCheckBehavior authCheckBehavior) {
    jobState = state.getJobState();
    this.canWriteEventOfLength = canWriteEventOfLength;
    jobVariablesCollector = new JobVariablesCollector(state);
    this.authCheckBehavior = authCheckBehavior;
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
   * @return the amount of activated jobs per jobKind on success, or a job which was too large to
   *     activate on failure
   */
  public Either<TooLargeJob, Map<JobKind, Integer>> collectJobs(
      final TypedRecord<JobBatchRecord> record) {
    final JobBatchRecord value = record.getValue();
    final ValueArray<JobRecord> jobIterator = value.jobs();
    final ValueArray<LongValue> jobKeyIterator = value.jobKeys();
    final Collection<DirectBuffer> requestedVariables = collectVariableNames(value);
    final var maxActivatedCount = value.getMaxJobsToActivate();
    final var activatedCount = new MutableInteger(0);
    final var unwritableJob = new MutableReference<TooLargeJob>();
    final var tenantIds =
        value.getTenantIds().isEmpty()
            ? List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            : value.getTenantIds();
    final Map<JobKind, Integer> jobCountPerJobKind = new EnumMap<>(JobKind.class);
    // the tenant check is performed earlier in the JobBatchActivateProcessor, so we can skip it
    // here and only check if the requester has the correct permissions to access the jobs
    final var authorizedProcessIds =
        authCheckBehavior.getAllAuthorizedScopes(
            new AuthorizationRequest(
                record,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE));

    jobState.forEachActivatableJobs(
        value.getTypeBuffer(),
        tenantIds,
        (key, jobRecord) -> {
          if (!isAuthorizedForJob(jobRecord, authorizedProcessIds)) {
            // Skip Jobs the user is not authorized for
            return true;
          }

          // fill in the job record properties first in order to accurately estimate its size before
          // adding it to the batch
          final var deadline = record.getTimestamp() + value.getTimeout();
          jobRecord.setDeadline(deadline).setWorker(value.getWorkerBuffer());
          jobVariablesCollector.setJobVariables(requestedVariables, jobRecord);

          // the expected length is based on the current record's length plus the length of the job
          // record we would add to the batch, the number of bytes taken by the additional job key,
          // as well as an 8 KB buffer.
          final var jobRecordLength = jobRecord.getLength();
          final var expectedEventLength =
              record.getLength()
                  + jobRecordLength
                  + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
          if (activatedCount.value <= maxActivatedCount
              && canWriteEventOfLength.test(expectedEventLength)) {
            appendJobToBatch(jobIterator, jobKeyIterator, key, jobRecord);
            activatedCount.increment();

            // track the count of activated jobs by their JobKind
            jobCountPerJobKind.merge(jobRecord.getJobKind(), 1, Integer::sum);

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

    return Either.right(jobCountPerJobKind);
  }

  private boolean isAuthorizedForJob(
      final JobRecord jobRecord, final Set<AuthorizationScope> authorizedProcessIds) {
    return authorizedProcessIds.contains(AuthorizationScope.WILDCARD)
        || authorizedProcessIds.contains(AuthorizationScope.id(jobRecord.getBpmnProcessId()));
  }

  private void appendJobToBatch(
      final ValueArray<JobRecord> jobIterator,
      final ValueArray<LongValue> jobKeyIterator,
      final long key,
      final JobRecord jobRecord) {
    jobKeyIterator.add().setValue(key);
    jobIterator.add().copyFrom(jobRecord);
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
