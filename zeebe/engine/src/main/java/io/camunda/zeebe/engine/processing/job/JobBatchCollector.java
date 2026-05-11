/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.secret.SecretStore;
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
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.ArrayList;
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
  private final InstantSource clock;

  /**
   * @param canWriteEventOfLength a predicate which should return whether the resulting {@link
   *     TypedRecord} containing the {@link JobBatchRecord} will be writable or not. The predicate
   *     takes in the size of the record, and should return true if it can write such a record, and
   *     false otherwise
   */
  JobBatchCollector(
      final ProcessingState state,
      final Predicate<Integer> canWriteEventOfLength,
      final AuthorizationCheckBehavior authCheckBehavior,
      final InstantSource clock,
      final SecretStore secretStore) {
    jobState = state.getJobState();
    this.canWriteEventOfLength = canWriteEventOfLength;
    jobVariablesCollector = new JobVariablesCollector(state, secretStore);
    this.authCheckBehavior = authCheckBehavior;
    this.clock = clock;
  }

  /**
   * Collects jobs to be added to the given {@code record}. The jobs and their keys are added
   * directly to the given record.
   *
   * <p>Two per-job failure modes are surfaced on the returned {@link CollectionResult}:
   *
   * <ul>
   *   <li>{@code tooLargeJob} — set if the very first job could not fit into a single batch record;
   *       all activation is aborted at that point.
   *   <li>{@code secretFailures} — list of jobs whose {@code fetchVariables} requested a {@code
   *       camunda.secret.X} reference that the store could not resolve. These jobs are skipped (not
   *       added to the batch); the rest of the iteration continues.
   * </ul>
   *
   * <p>The caller is responsible for raising incidents for both failure modes; this collector is a
   * pure data-gathering step.
   */
  public CollectionResult collectJobs(
      final TypedRecord<JobBatchRecord> record, final List<String> tenantIds) {
    final JobBatchRecord value = record.getValue();
    final ValueArray<JobRecord> jobIterator = value.jobs();
    final ValueArray<LongValue> jobKeyIterator = value.jobKeys();
    final Collection<DirectBuffer> requestedVariables = collectVariableNames(value);
    final var maxActivatedCount = value.getMaxJobsToActivate();
    final var activatedCount = new MutableInteger(0);
    final var unwritableJob = new MutableReference<TooLargeJob>();
    final List<JobWithMissingSecret> secretFailures = new ArrayList<>();
    final Map<JobKind, Integer> jobCountPerJobKind = new EnumMap<>(JobKind.class);
    // the tenant check is performed earlier in the JobBatchActivateProcessor, so we can skip it
    // here and only check if the requester has the correct permissions to access the jobs
    final var authorizedProcessIds =
        authCheckBehavior.getAllAuthorizedScopes(
            AuthorizationRequest.builder()
                .command(record)
                .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
                .build());
    final var deadline = clock.millis() + value.getTimeout();

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
          jobRecord.setDeadline(deadline).setWorker(value.getWorkerBuffer());
          final var missingSecret =
              jobVariablesCollector.setJobVariables(requestedVariables, jobRecord);
          if (missingSecret.isPresent()) {
            // Per-job failure — record it for an incident, skip this job, keep iterating so
            // unaffected jobs in the same batch still activate.
            secretFailures.add(
                new JobWithMissingSecret(key, cloneJobRecord(jobRecord), missingSecret.get()));
            return activatedCount.value < maxActivatedCount;
          }

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

    return new CollectionResult(jobCountPerJobKind, unwritableJob.ref, secretFailures);
  }

  private static JobRecord cloneJobRecord(final JobRecord source) {
    // The visitor reuses a single JobRecord instance across iterations; we keep our own copy so
    // the incident downstream sees the failed job's properties, not whichever job comes next.
    final var copy = new JobRecord();
    copy.copyFrom(source);
    return copy;
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

  record JobWithMissingSecret(long key, JobRecord jobRecord, String missingSecretName) {}

  /**
   * Aggregate outcome of {@link #collectJobs(TypedRecord, List)}. {@code tooLargeJob} is at most
   * one (only the first job seen, since the iteration aborts after); {@code secretFailures} lists
   * every job whose secret resolution failed, since we keep iterating past those.
   */
  record CollectionResult(
      Map<JobKind, Integer> activatedJobCountPerJobKind,
      TooLargeJob tooLargeJob,
      List<JobWithMissingSecret> secretFailures) {}
}
