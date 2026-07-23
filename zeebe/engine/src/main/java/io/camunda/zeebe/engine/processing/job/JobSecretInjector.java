/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static java.util.Comparator.comparingInt;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects cached secret values into the variables of activated jobs, in two steps before the
 * ACTIVATED event is appended:
 *
 * <ol>
 *   <li>During batch collection, {@link #checkSecrets} looks up the secret references of every job
 *       (stored on the {@link JobRecord} at creation) in the per-store secret caches of the {@link
 *       SecretStoreRegistry}. Jobs with an uncached reference are skipped by the collector without
 *       consuming a batch slot, so jobs behind them can still be activated; jobs whose references
 *       are all cached are appended and registered via {@link #registerForInjection}.
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
 * <p>The replacement is textual: every occurrence of the placeholder in the addressed leaf is
 * replaced, including text that merely spells out the placeholder next to a real reference at the
 * same path. This can only surface a secret in a leaf that already receives that secret.
 *
 * <p>Only the long-poll activation response is covered. The job-push path (see {@code
 * BpmnJobActivationBehavior#publishWork}) is out of scope for this change and still streams the
 * unresolved placeholders.
 */
public final class JobSecretInjector {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobSecretInjector.class.getPackageName());
  private static final String SECRET_REFERENCE_PREFIX = "camunda.secrets.";

  /** Reads and writes the msgpack encoding of job variables as Jackson trees. */
  private final ObjectMapper variablesMapper = new ObjectMapper(new MessagePackFactory());

  private final Map<String, SecretCache> caches;

  // accumulated per activation command while the collector checks and appends jobs, consumed
  // (and reset) by injectSecretValues; the collector's reset() bounds a new command
  private final Map<SecretReference, String> values = new HashMap<>();
  private final List<JobWithCachedSecrets> jobsWithCachedSecrets = new ArrayList<>();

  public JobSecretInjector(final SecretStoreRegistry secretStoreRegistry) {
    caches = secretStoreRegistry.getCaches();
  }

  /** Discards any state accumulated for a previous activation command. */
  public void reset() {
    values.clear();
    jobsWithCachedSecrets.clear();
  }

  /**
   * Checks each secret reference of the job (stored on the {@link JobRecord} at creation) for a
   * cached value, materializing the values and the job's secrets once. Every reference is checked,
   * even after the first miss, so the caller sees all non-cached references of the job. A cache
   * lookup failure propagates to the caller and fails the activation command.
   *
   * <p>TODO(https://github.com/camunda/camunda/issues/57846): the skipped jobs stay activatable,
   * which can make a long poll collect them again right away. Instead, mark them as waiting for
   * secret resolution and request the background resolution of their non-cached references.
   */
  public SecretCheckResult checkSecrets(final JobRecord job) {
    if (!job.hasSecretReferences()) {
      return SecretCheckResult.NO_SECRETS;
    }
    final List<Secret> cachedSecrets = new ArrayList<>();
    final List<Secret> nonCachedSecrets = new ArrayList<>();
    for (final Secret secret : secretsOf(job)) {
      if (resolveIntoValues(secret.reference())) {
        cachedSecrets.add(secret);
      } else {
        nonCachedSecrets.add(secret);
      }
    }
    return new SecretCheckResult(cachedSecrets, nonCachedSecrets);
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

  /** Returns whether any job registered since the last reset has cached secret values to inject. */
  public boolean hasSecretsToInject() {
    return !jobsWithCachedSecrets.isEmpty();
  }

  /**
   * Resolves the reference into the materialized values, or returns {@code false} when it has no
   * cached value. A reference resolved before is not looked up again. A cache lookup failure is
   * deliberately not caught here: it propagates and fails the activation command.
   */
  private boolean resolveIntoValues(final SecretReference reference) {
    if (values.containsKey(reference)) {
      return true;
    }
    final SecretCache cache = cacheOf(reference.storeId());
    if (cache == null) {
      return false;
    }
    final Optional<String> value = cache.get(reference.name());
    value.ifPresent(cachedValue -> values.put(reference, cachedValue));
    return value.isPresent();
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
          injected = injectedVariablesOf(jobWithSecrets);
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
   * Returns the cache of the store holding the referenced secret, or {@code null} when no store
   * matches. The {@code camunda.secrets.<name>} syntax carries no store dimension yet, so an empty
   * store ID addresses the sole configured store; with several stores an empty store ID is
   * ambiguous and does not resolve.
   */
  private SecretCache cacheOf(final String storeId) {
    if (!storeId.isEmpty()) {
      return caches.get(storeId);
    }
    return caches.size() == 1 ? caches.values().iterator().next() : null;
  }

  /**
   * Returns the job's variables with every secret placeholder replaced by its cached value, or
   * {@code null} when no placeholder was found. A failure propagates to the caller, which drops the
   * job from the activation and reports it for an incident.
   */
  private byte[] injectedVariablesOf(final JobWithCachedSecrets jobWithSecrets) throws IOException {
    final DirectBuffer variables = jobWithSecrets.job().getVariablesBuffer();
    final JsonNode document = variablesMapper.readTree(new DirectBufferInputStream(variables));
    // a placeholder can be absent (e.g. fetchVariables excluded the variable); an unchanged job
    // must keep its original encoding and stay out of the growth budget, so it returns null
    boolean changed = false;
    for (final Secret secret : jobWithSecrets.cachedSecrets()) {
      changed |= replaceInLeaf(document, secret, values.get(secret.reference()));
    }
    return changed ? variablesMapper.writeValueAsBytes(document) : null;
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
   * Replaces the placeholder in the text leaf addressed by the secret's JSON pointer. Pointers that
   * do not address a text leaf of an object (e.g. the path no longer matches the variables, or it
   * runs into an array) are skipped defensively.
   */
  private static boolean replaceInLeaf(
      final JsonNode document, final Secret secret, final String value) {
    if (!secret.path().startsWith("/")) {
      return false;
    }
    final JsonPointer pointer = JsonPointer.compile(secret.path());
    final JsonNode parent = document.at(pointer.head());
    final JsonNode leaf = document.at(pointer);
    if (!parent.isObject() || !leaf.isTextual()) {
      return false;
    }
    final String text = leaf.textValue();
    final String replaced = text.replace(secret.placeholder(), value);
    if (replaced.equals(text)) {
      return false;
    }
    ((ObjectNode) parent).put(pointer.last().getMatchingProperty(), replaced);
    return true;
  }

  /**
   * Materializes the job's secret references, longest placeholder first so a reference name that is
   * a prefix of another (e.g. {@code token} vs {@code token2}) cannot corrupt the longer
   * placeholder when both are injected into the same leaf.
   */
  private static List<Secret> secretsOf(final JobRecord job) {
    final List<Secret> secrets = new ArrayList<>();
    for (final JobSecretReference reference : job.secretReferences()) {
      secrets.add(
          new Secret(
              new SecretReference(reference.getStoreId(), reference.getSecretReference()),
              reference.getPath(),
              SECRET_REFERENCE_PREFIX + reference.getSecretReference()));
    }
    if (secrets.size() > 1) {
      secrets.sort(comparingInt((Secret secret) -> secret.placeholder().length()).reversed());
    }
    return secrets;
  }

  record Secret(SecretReference reference, String path, String placeholder) {}

  /**
   * The result of {@link #checkSecrets} for one job: the job's secrets with a cached value and
   * those without one, each materialized once (both empty for a job without secret references). A
   * job with any non-cached secret must not be activated.
   */
  public record SecretCheckResult(List<Secret> cachedSecrets, List<Secret> nonCachedSecrets) {
    static final SecretCheckResult NO_SECRETS = new SecretCheckResult(List.of(), List.of());
  }

  /** A registered job: its index in the batch, the job, and its cached secrets. */
  record JobWithCachedSecrets(int index, JobRecord job, List<Secret> cachedSecrets) {}

  /** A job dropped from the activation batch that the processor must raise an incident for. */
  public sealed interface DroppedJob permits OversizedJob, FailedInjectionJob {
    long jobKey();

    JobRecord job();
  }

  /**
   * A job dropped from the batch whose secret values can never fit: injecting them would grow the
   * batch by {@code growth} bytes, more than the whole budget any activation batch has to spare.
   */
  public record OversizedJob(long jobKey, JobRecord job, int growth) implements DroppedJob {}

  /** A job dropped from the batch because injecting its secret values failed. */
  public record FailedInjectionJob(long jobKey, JobRecord job) implements DroppedJob {}

  /** The first job removed by {@link #dropJobsFromIndex}, i.e. the one at the given index. */
  private record RemovedJob(long jobKey, JobRecord job) {}
}
