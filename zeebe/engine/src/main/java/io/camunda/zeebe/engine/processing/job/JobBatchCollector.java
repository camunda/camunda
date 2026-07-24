/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  private final CslAuthorizationCheck cslCheck;
  private final Predicate<Integer> canWriteEventOfLength;
  private final InstantSource clock;
  private final JobProcessingMetrics jobMetrics;
  private final JobSecretInjector jobSecretInjector;

  /**
   * @param canWriteEventOfLength a predicate which should return whether the resulting {@link
   *     TypedRecord} containing the {@link JobBatchRecord} will be writable or not. The predicate
   *     takes in the size of the record, and should return true if it can write such a record, and
   *     false otherwise
   * @param jobSecretInjector checks the secret references of every job against the secret caches;
   *     jobs with an uncached reference are skipped without consuming a batch slot
   */
  JobBatchCollector(
      final ProcessingState state,
      final Predicate<Integer> canWriteEventOfLength,
      final CslAuthorizationCheck cslCheck,
      final InstantSource clock,
      final JobProcessingMetrics jobMetrics,
      final JobSecretInjector jobSecretInjector) {
    jobState = state.getJobState();
    this.canWriteEventOfLength = canWriteEventOfLength;
    jobVariablesCollector = new JobVariablesCollector(state);
    this.cslCheck = cslCheck;
    this.clock = clock;
    this.jobMetrics = jobMetrics;
    this.jobSecretInjector = jobSecretInjector;
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
   * @param tenantIds the tenant IDs to use for filtering jobs
   * @return the amount of activated jobs per jobKind on success, or a job which was too large to
   *     activate on failure
   */
  public Either<TooLargeJob, Map<JobKind, Integer>> collectJobs(
      final TypedRecord<JobBatchRecord> record, final List<String> tenantIds) {
    final JobBatchRecord value = record.getValue();
    final ValueArray<JobRecord> jobIterator = value.jobs();
    final ValueArray<LongValue> jobKeyIterator = value.jobKeys();
    final Collection<DirectBuffer> requestedVariables = collectVariableNames(value);
    final var maxActivatedCount = value.getMaxJobsToActivate();
    final var activatedCount = new MutableInteger(0);
    final var skippedUncachedSecretJobs = new MutableInteger(0);
    final var unwritableJob = new MutableReference<TooLargeJob>();
    final Map<JobKind, Integer> jobCountPerJobKind = new EnumMap<>(JobKind.class);
    final var deadline = clock.millis() + value.getTimeout();

    // compute per-job authorization predicate once before the loop
    final Predicate<JobRecord> isAuthorizedForJob = buildAuthzPredicate(record);

    jobSecretInjector.reset();
    jobState.forEachActivatableJobs(
        value.getTypeBuffer(),
        tenantIds,
        (key, jobRecord) -> {
          if (!isAuthorizedForJob.test(jobRecord)) {
            // Skip jobs the requester is not authorized for
            return true;
          }

          if (!value.isWithLease() && !jobRecord.getLeaseToken().isEmpty()) {
            // Skip leased jobs so an unleased activation cannot break the lease's exclusivity
            jobMetrics.countJobEvent(JobAction.SKIPPED, jobRecord.getJobKind(), value.getType());
            return true;
          }

          final var secretCheckResult = jobSecretInjector.checkSecrets(jobRecord);
          if (!secretCheckResult.nonCachedSecrets().isEmpty()) {
            // Skip jobs with an uncached secret reference without consuming a batch slot, so the
            // jobs behind them can still be activated
            jobMetrics.countJobEvent(JobAction.SKIPPED, jobRecord.getJobKind(), value.getType());
            skippedUncachedSecretJobs.increment();
            return skippedUncachedSecretJobs.value
                < EngineConfiguration.MAX_UNCACHED_SECRET_JOBS_SKIPPED_PER_ACTIVATION;
          }

          // fill in the job record properties first in order to accurately estimate its size before
          // adding it to the batch
          jobRecord.setDeadline(deadline).setWorker(value.getWorkerBuffer());
          if (value.isWithLease()) {
            jobRecord.setLeaseToken(generateLeaseToken());
          }
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
            final var appendedJob = appendJobToBatch(jobIterator, jobKeyIterator, key, jobRecord);
            jobSecretInjector.registerForInjection(
                secretCheckResult, activatedCount.value, appendedJob);
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

  private Predicate<JobRecord> buildAuthzPredicate(final TypedRecord<JobBatchRecord> record) {
    final var resolved =
        cslCheck.resolveForCheck(record, AuthorizationRejectionMapper.noPrincipal());
    if (resolved.isLeft()) {
      return job -> false;
    }
    final var maybeAuth = resolved.get();
    if (maybeAuth.isEmpty()) {
      return job -> true;
    }
    final var auth = maybeAuth.get();
    return job ->
        cslCheck
            .checkAuth(
                auth,
                RequiredAuthorization.of(
                    b ->
                        b.processDefinition()
                            .updateProcessInstance()
                            .resourceId(job.getBpmnProcessId())))
            .isRight();
  }

  private JobRecord appendJobToBatch(
      final ValueArray<JobRecord> jobIterator,
      final ValueArray<LongValue> jobKeyIterator,
      final long key,
      final JobRecord jobRecord) {
    jobKeyIterator.add().setValue(key);
    final JobRecord appendedJob = jobIterator.add();
    appendedJob.copyFrom(jobRecord);
    return appendedJob;
  }

  /**
   * Generates a lease token for a single activated job. The token is an opaque string that callers
   * must not parse; it is random with enough entropy that collisions between leased jobs are
   * negligible.
   */
  private static String generateLeaseToken() {
    return UUID.randomUUID().toString();
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
