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
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.CachedSecret;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretCheckResult;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretPointerMismatchException;
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
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
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
 *       pointer recorded for each reference (see {@link JobSecretLookup} for how a reference that
 *       finds no placeholder is tolerated). Jobs whose values would grow the response beyond the
 *       max message size are dropped from the activation instead, and a job whose injection fails
 *       is dropped and reported for an incident, naming the mismatched pointer when it is known.
 * </ol>
 *
 * <p>The cached values are read once at check time and reused for the injection, so a value evicted
 * from the cache in between cannot drop a job late. The injected values must only ever reach the
 * record that is written to the activation response and nowhere else. That keeps the secret values
 * on the response only: state, records, and logs keep the placeholders.
 *
 * <p>The job-push path injects the same values into the job it streams, see {@code
 * BpmnJobActivationBehavior#publishWork}; it shares {@link JobSecretLookup}, so it tolerates the
 * same shapes, and {@link JobSecretInjectionIncident}, so it reports a failed injection with the
 * same message.
 */
public final class JobSecretInjector {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobSecretInjector.class.getPackageName());

  private final JobSecretLookup secretLookup;

  // accumulated per activation command while the collector checks and appends jobs, consumed
  // (and reset) by injectSecretValues; the collector's reset() bounds a new command
  private final List<JobWithCachedSecrets> jobsWithCachedSecrets = new ArrayList<>();

  // jobs skipped for a non-cached secret reference, grouped by reference so the processor can emit
  // one RESOLUTION_REQUESTED event per reference; insertion-ordered for a deterministic emission
  private final Map<SecretReference, List<Long>> jobsWithNonCachedSecrets = new LinkedHashMap<>();

  public JobSecretInjector(final SecretStoreRegistry secretStoreRegistry) {
    secretLookup = new JobSecretLookup(secretStoreRegistry);
  }

  /** Discards any state accumulated for a previous activation command. */
  public void reset() {
    jobsWithCachedSecrets.clear();
    jobsWithNonCachedSecrets.clear();
  }

  /**
   * Checks the job's secret references for cached values, see {@link
   * JobSecretLookup#check(JobRecord)}.
   *
   * <p>The collector skips a job with any non-cached reference and registers it via {@link
   * #registerForResolution}, so the processor can request the background resolution of the
   * non-cached references and park the job until it is resolved.
   */
  public SecretCheckResult checkSecrets(final JobRecord job) {
    return secretLookup.check(job);
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
   * <p>The injected values ride the activation response only, which must stay under the max message
   * size like the appended event. The collector already sized the appended event (with the
   * placeholders) against that limit, keeping {@link
   * EngineConfiguration#BATCH_SIZE_CALCULATION_BUFFER} bytes of slack as a margin for the framing
   * metadata it cannot measure. Here each job's value growth is measured against the real remaining
   * space up to that limit, not against the margin: {@code baseLength} is the size the batch
   * already occupies, and {@code canWriteEventOfLength} is the same predicate the collector used. A
   * job is kept only while {@code baseLength + accumulatedGrowth + growth + margin} still fits; the
   * first job whose values would push the response past that is dropped together with every job
   * after it, from the response batch and the to-be-activated batch alike (both must still contain
   * the same jobs), so the dropped jobs stay activatable and the next activation, with a fresh
   * batch, picks them up right away. A job whose injection fails is dropped the same way; the
   * exception's own message is only logged, but its placeholder and JSON pointer (never its value)
   * are carried out for the incident message. Must run before the ACTIVATED event is appended.
   * Returns the dropped job the caller must raise an incident for: a job whose injection failed, or
   * a job whose values can never fit any batch.
   *
   * @param baseLength the length the batch already occupies before any value is injected, i.e. the
   *     length of the collected activation command; the injected growth is measured on top of it
   * @param canWriteEventOfLength returns whether a record of the given length still fits the max
   *     message size, the same proxy the collector uses to size the appended event
   */
  public Optional<DroppedJob> injectSecretValues(
      final JobBatchRecord responseBatch,
      final JobBatchRecord activatedBatch,
      final int baseLength,
      final Predicate<Integer> canWriteEventOfLength) {
    try {
      int accumulatedGrowth = 0;
      for (final JobWithCachedSecrets jobWithSecrets : jobsWithCachedSecrets) {
        final byte[] injected;
        try {
          injected =
              secretLookup.injectedVariablesOf(
                  jobWithSecrets.job(), jobWithSecrets.cachedSecrets());
        } catch (final Exception e) {
          return Optional.of(
              dropJobsWhoseInjectionFailed(responseBatch, activatedBatch, jobWithSecrets, e));
        }
        if (injected == null) {
          continue;
        }
        final int growth = injected.length - jobWithSecrets.job().getVariablesBuffer().capacity();
        final int occupiedLength = baseLength + accumulatedGrowth;
        if (!canWriteEventOfLength.test(
            occupiedLength + growth + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
          return dropJobsThatNoLongerFit(
              responseBatch, activatedBatch, jobWithSecrets.index(), growth, occupiedLength);
        }
        // the registered job belongs to the to-be-activated batch; the response element at the
        // same index carries the same variables until they are replaced here
        responseBatch
            .jobs()
            .get(jobWithSecrets.index())
            .setVariables(BufferUtil.wrapArray(injected));
        accumulatedGrowth += growth;
      }
      return Optional.empty();
    } finally {
      reset();
    }
  }

  /**
   * Drops the job whose injection failed and every job after it from both batches, and returns it
   * for an incident, so the job cannot loop through activation with a failing injection. The
   * exception's own message is only logged; only its placeholder and JSON pointer are carried out
   * for the incident message (see {@link JobSecretInjectionIncident}).
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
    if (failure instanceof final SecretPointerMismatchException mismatch) {
      return new FailedInjectionJob(
          removed.jobKey(), removed.job(), mismatch.path(), mismatch.placeholder());
    }
    return new FailedInjectionJob(removed.jobKey(), removed.job(), null, null);
  }

  /**
   * Drops the job at the given index and every job after it from both batches, so none of them are
   * activated and all stay activatable, and marks both batches as truncated so the client polls for
   * the dropped jobs right away. A job dropped as the first of the batch had the whole message size
   * to itself, so its values can never fit any batch: it is returned so the caller can raise an
   * incident for it, just like for a job that is too large to activate without secrets.
   */
  private static Optional<DroppedJob> dropJobsThatNoLongerFit(
      final JobBatchRecord responseBatch,
      final JobBatchRecord activatedBatch,
      final int index,
      final int growth,
      final int occupiedLength) {
    final RemovedJob removed = dropJobsFromIndex(responseBatch, activatedBatch, index);
    LOGGER.warn(
        "Not activating the job with key {} of type '{}' and the jobs after it in the batch: "
            + "injecting the job's secret values would grow the activation batch by {} bytes on top "
            + "of the {} bytes it already occupies, which would exceed the max message size. The "
            + "dropped jobs stay activatable",
        removed.jobKey(),
        removed.job().getType(),
        growth,
        occupiedLength);
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
   * batch by {@code growth} bytes, past the max message size even in an otherwise empty batch.
   */
  public record OversizedJob(long jobKey, JobRecord job, int growth) implements DroppedJob {}

  /**
   * A job dropped from the batch because injecting its secret values failed. {@code path} and
   * {@code placeholder} identify the secret whose pointer didn't match, or are both {@code null}
   * for an unrelated failure (e.g. the job's variables are not valid msgpack).
   */
  public record FailedInjectionJob(
      long jobKey, JobRecord job, @Nullable String path, @Nullable String placeholder)
      implements DroppedJob {}

  /** A registered job: its index in the batch, the job, and its cached secrets. */
  record JobWithCachedSecrets(int index, JobRecord job, List<CachedSecret> cachedSecrets) {}

  /** The first job removed by {@link #dropJobsFromIndex}, i.e. the one at the given index. */
  private record RemovedJob(long jobKey, JobRecord job) {}

  /** A job dropped from the activation batch that the processor must raise an incident for. */
  public sealed interface DroppedJob permits OversizedJob, FailedInjectionJob {
    long jobKey();

    JobRecord job();
  }
}
