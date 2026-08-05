/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.job.JobSecretValues.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretValues.SecretCheckResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects cached secret values into the variables of the jobs activated by a long poll, in two
 * steps before the ACTIVATED event is appended:
 *
 * <ol>
 *   <li>During batch collection, {@link #checkSecrets} looks up the secret references of every job
 *       (stored on the {@link JobRecord} at creation) in what the stores of the {@link
 *       SecretStoreRegistry} already hold locally, reading no store: this runs on the stream
 *       processor, where blocking on store I/O would stall processing. Jobs with a reference no
 *       store holds are skipped by the collector without consuming a batch slot, so jobs behind
 *       them can still be activated; jobs whose references are all cached are appended and
 *       registered via {@link #registerForInjection}.
 *   <li>{@link #injectSecretValues} replaces the placeholder text {@code camunda.secrets.<name>} of
 *       the registered jobs with the cached value on a response-only copy of the batch, at the JSON
 *       pointer recorded for each reference. Jobs whose values would grow the response beyond the
 *       max message size are dropped from the activation instead, and a job whose injection fails
 *       is dropped and reported for an incident.
 * </ol>
 *
 * <p>The cached values are materialized once at check time and reused for the injection, so a value
 * evicted from the cache in between cannot drop a job late. The injected values must only ever
 * reach the record that is written to the activation response and nowhere else. That keeps the
 * secret values on the response only: state, records, and logs keep the placeholders.
 *
 * <p>The job-push path injects the same values into the job it streams, see {@code
 * BpmnJobActivationBehavior#publishWork}.
 */
public final class JobSecretInjector {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobSecretInjector.class.getPackageName());

  private final JobSecretValues secretValues;

  // accumulated per activation command while the collector checks and appends jobs, consumed
  // (and reset) by injectSecretValues; the collector's reset() bounds a new command
  private final List<JobWithCachedSecrets> jobsWithCachedSecrets = new ArrayList<>();

  // jobs skipped for a non-cached secret reference, grouped by reference so the processor can emit
  // one RESOLUTION_REQUESTED event per reference; insertion-ordered for a deterministic emission
  private final Map<SecretReference, List<Long>> jobsWithNonCachedSecrets = new LinkedHashMap<>();

  public JobSecretInjector(final SecretStoreRegistry secretStoreRegistry) {
    secretValues = new JobSecretValues(secretStoreRegistry);
  }

  /** Discards any state accumulated for a previous activation command. */
  public void reset() {
    secretValues.reset();
    jobsWithCachedSecrets.clear();
    jobsWithNonCachedSecrets.clear();
  }

  /**
   * Checks the job's secret references for cached values, see {@link
   * JobSecretValues#check(JobRecord)}.
   *
   * <p>The collector skips a job with any non-cached reference and registers it via {@link
   * #registerForResolution}, so the processor can request the background resolution of the
   * non-cached references and park the job until it is resolved.
   */
  public SecretCheckResult checkSecrets(final JobRecord job) {
    return secretValues.check(job);
  }

  /**
   * Registers a job appended to the batch for the value injection of {@link #injectSecretValues},
   * with its position in the batch and the appended {@link JobRecord} element (whose variables the
   * injection reads). Only a job whose references are all cached is registered; a check without
   * secrets registers nothing.
   */
  public void registerForInjection(
      final SecretCheckResult check, final int batchIndex, final JobRecord appendedJob) {
    if (check.nonCachedSecrets().isEmpty() && !check.cachedSecrets().isEmpty()) {
      jobsWithCachedSecrets.add(
          new JobWithCachedSecrets(batchIndex, appendedJob, check.cachedSecrets()));
    }
  }

  /**
   * Registers a job skipped by the collector because some of its secret references have no cached
   * value, grouping the job's key under each of its non-cached references. The processor emits one
   * {@code RESOLUTION_REQUESTED} event per reference with the keys of the jobs waiting on it. A
   * reference that occurs more than once for the same job (e.g. at two paths) records the job once.
   */
  public void registerForResolution(final SecretCheckResult check, final long jobKey) {
    final Set<SecretReference> seen = new HashSet<>();
    for (final Secret secret : check.nonCachedSecrets()) {
      if (seen.add(secret.reference())) {
        jobsWithNonCachedSecrets
            .computeIfAbsent(secret.reference(), reference -> new ArrayList<>())
            .add(jobKey);
      }
    }
  }

  /** Returns whether any job registered since the last reset has cached secret values to inject. */
  public boolean hasSecretsToInject() {
    return !jobsWithCachedSecrets.isEmpty();
  }

  /**
   * Returns the keys of the jobs waiting on each non-cached secret reference, grouped by reference
   * in registration order. The processor takes this snapshot before {@link #injectSecretValues}
   * resets the injector; the snapshot is immutable and detached from the reset, so the caller keeps
   * the keys registered at the time of the call.
   */
  public Map<SecretReference, List<Long>> jobsWithNonCachedSecrets() {
    final Map<SecretReference, List<Long>> snapshot = new LinkedHashMap<>();
    jobsWithNonCachedSecrets.forEach(
        (reference, jobKeys) -> snapshot.put(reference, List.copyOf(jobKeys)));
    return Collections.unmodifiableMap(snapshot);
  }

  /**
   * Injects the cached values into the secret placeholders of the registered jobs, replacing the
   * variables of the response batch in place. Consumes the values and jobs accumulated since the
   * last reset (the engine processor is single-threaded, so the collection of a command always
   * completes before its injection) and resets this injector for the next activation command.
   *
   * <p>The collector sized the batch against the max message size with {@link
   * EngineConfiguration#BATCH_SIZE_CALCULATION_BUFFER} bytes of slack, which the injected values
   * may consume. The first job whose values would grow the response further is dropped together
   * with every job after it, from the response batch and the to-be-activated batch alike (both must
   * still contain the same jobs), so the dropped jobs stay activatable and the next activation,
   * with a fresh budget, picks them up right away. A job whose injection fails is dropped the same
   * way; its failure details are only logged, so no secret-related data can end up in persisted
   * records. Must run before the ACTIVATED event is appended. Returns the dropped job the caller
   * must raise an incident for: a job whose injection failed, or a job whose values can never fit
   * any batch.
   */
  public Optional<DroppedJob> injectSecretValues(
      final JobBatchRecord responseBatch, final JobBatchRecord activatedBatch) {
    try {
      int remainingGrowth = EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
      for (final JobWithCachedSecrets jobWithSecrets : jobsWithCachedSecrets) {
        final byte[] injected;
        try {
          injected =
              secretValues.injectedVariablesOf(
                  jobWithSecrets.job(), jobWithSecrets.cachedSecrets());
        } catch (final Exception e) {
          return Optional.of(
              dropJobsWhoseInjectionFailed(responseBatch, activatedBatch, jobWithSecrets, e));
        }
        if (injected == null) {
          continue;
        }
        final int growth = injected.length - jobWithSecrets.job().getVariablesBuffer().capacity();
        if (growth > remainingGrowth) {
          return dropJobsThatNoLongerFit(
              responseBatch, activatedBatch, jobWithSecrets.index(), growth, remainingGrowth);
        }
        // the registered job belongs to the to-be-activated batch; the response element at the
        // same index carries the same variables until they are replaced here
        responseBatch
            .jobs()
            .get(jobWithSecrets.index())
            .setVariables(BufferUtil.wrapArray(injected));
        remainingGrowth -= growth;
      }
      return Optional.empty();
    } finally {
      reset();
    }
  }

  /**
   * Drops the job whose injection failed and every job after it from both batches, and returns it
   * for an incident, so the job cannot loop through activation with a failing injection. The
   * failure details are only logged, so no secret-related data can end up in persisted records.
   */
  private static FailedInjectionJob dropJobsWhoseInjectionFailed(
      final JobBatchRecord responseBatch,
      final JobBatchRecord activatedBatch,
      final JobWithCachedSecrets jobWithSecrets,
      final Exception failure) {
    final RemovedJob removed =
        dropJobsFromIndex(responseBatch, activatedBatch, jobWithSecrets.index());
    LOGGER.warn(
        "Failed to inject secret values into the variables of the job with key {} of type '{}'; "
            + "the job is not activated and gets an incident. The jobs after it in the batch are "
            + "dropped from the activation too and stay activatable",
        removed.jobKey(),
        removed.job().getType(),
        failure);
    return new FailedInjectionJob(removed.jobKey(), removed.job());
  }

  /**
   * Drops the job at the given index and every job after it from both batches, so none of them are
   * activated and all stay activatable, and marks both batches as truncated so the client polls for
   * the dropped jobs right away. A job dropped as the first of the batch had the full growth budget
   * to itself, so its values can never fit any batch: it is returned so the caller can raise an
   * incident for it, just like for a job that is too large to activate without secrets.
   */
  private static Optional<DroppedJob> dropJobsThatNoLongerFit(
      final JobBatchRecord responseBatch,
      final JobBatchRecord activatedBatch,
      final int index,
      final int growth,
      final int remainingGrowth) {
    final RemovedJob removed = dropJobsFromIndex(responseBatch, activatedBatch, index);
    LOGGER.warn(
        "Not activating the job with key {} of type '{}' and the jobs after it in the batch: "
            + "injecting the job's secret values would grow the activation batch by {} bytes but "
            + "only {} bytes remain before the response could exceed the max message size. The "
            + "dropped jobs stay activatable",
        removed.jobKey(),
        removed.job().getType(),
        growth,
        remainingGrowth);
    return index == 0
        ? Optional.of(new OversizedJob(removed.jobKey(), removed.job(), growth))
        : Optional.empty();
  }

  /**
   * Removes the job at the given index and every job after it from both batches, and marks both
   * batches as truncated. Returns the removed job at the index; an index outside the batch fails
   * fast with an {@link IndexOutOfBoundsException}.
   */
  private static RemovedJob dropJobsFromIndex(
      final JobBatchRecord responseBatch, final JobBatchRecord activatedBatch, final int index) {
    for (int i = activatedBatch.jobs().size() - 1; i > index; i--) {
      responseBatch.jobs().remove(i);
      responseBatch.jobKeys().remove(i);
      activatedBatch.jobs().remove(i);
      activatedBatch.jobKeys().remove(i);
    }
    responseBatch.jobs().remove(index);
    responseBatch.jobKeys().remove(index);
    final JobRecord removedJob = activatedBatch.jobs().remove(index);
    final long removedJobKey = activatedBatch.jobKeys().remove(index).getValue();
    responseBatch.setTruncated(true);
    activatedBatch.setTruncated(true);
    return new RemovedJob(removedJobKey, removedJob);
  }

  /**
   * A job dropped from the batch whose secret values can never fit: injecting them would grow the
   * batch by {@code growth} bytes, more than the whole budget any activation batch has to spare.
   */
  public record OversizedJob(long jobKey, JobRecord job, int growth) implements DroppedJob {}

  /** A job dropped from the batch because injecting its secret values failed. */
  public record FailedInjectionJob(long jobKey, JobRecord job) implements DroppedJob {}

  /** A registered job: its index in the batch, the job, and its cached secrets. */
  record JobWithCachedSecrets(int index, JobRecord job, List<Secret> cachedSecrets) {}

  /** The first job removed by {@link #dropJobsFromIndex}, i.e. the one at the given index. */
  private record RemovedJob(long jobKey, JobRecord job) {}

  /** A job dropped from the activation batch that the processor must raise an incident for. */
  public sealed interface DroppedJob permits OversizedJob, FailedInjectionJob {
    long jobKey();

    JobRecord job();
  }
}
