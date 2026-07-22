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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.job.SecretResolver.SecretReference;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 *   <li>{@link #removeJobsWithUncachedSecrets} looks up the secret references of every job (stored
 *       on the {@link JobRecord} at creation) in the secret cache via the {@link SecretResolver}
 *       and removes the jobs with an uncached reference from the batch, so they are not activated.
 *   <li>{@link #injectSecretValues} replaces the placeholder text {@code camunda.secrets.<name>} of
 *       the remaining jobs with the cached value on a response-only copy of the batch, at the JSON
 *       pointer recorded for each reference. Jobs whose values would grow the response beyond the
 *       max message size are dropped from the activation instead.
 * </ol>
 *
 * <p>Both steps modify the given batches in place. The injected values must only ever reach the
 * record that is written to the activation response and nowhere else. That keeps the secret values
 * on the response only: state, records, and logs keep the placeholders.
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

  private final SecretResolver secretResolver;

  public JobSecretInjector(final SecretResolver secretResolver) {
    this.secretResolver = secretResolver;
  }

  /**
   * Removes every job with a secret reference that has no cached value from the batch (together
   * with its job key), so those jobs are not activated. Must run before the ACTIVATED event is
   * appended. If the cache lookup fails, every job with secret references is removed. Returns the
   * preparation for {@link #injectSecretValues} and the caller's {@code RESOLUTION_REQUESTED}
   * requests: the cached values, the surviving jobs with secret references materialized once, and
   * the uncached references grouped with the keys of the removed jobs that await them.
   */
  public Preparation removeJobsWithUncachedSecrets(final JobBatchRecord batch) {
    final List<PendingJob> candidates = collectPendingJobs(batch);
    if (candidates.isEmpty()) {
      return Preparation.NONE;
    }
    final Map<SecretReference, String> values = resolve(referencesOf(candidates));

    final List<PendingJob> pendingJobs = new ArrayList<>(candidates.size());
    final List<Integer> uncachedJobs = new ArrayList<>();
    final Map<SecretReference, List<Long>> missingSecrets = new LinkedHashMap<>();
    for (final PendingJob candidate : candidates) {
      final List<SecretReference> uncached = uncachedReferences(candidate, values);
      if (uncached.isEmpty()) {
        // only the removed jobs before a surviving job shift its index to the left
        pendingJobs.add(candidate.atIndex(candidate.index() - uncachedJobs.size()));
      } else {
        uncachedJobs.add(candidate.index());
        for (final SecretReference reference : uncached) {
          missingSecrets.computeIfAbsent(reference, k -> new ArrayList<>()).add(candidate.jobKey());
        }
      }
    }
    // remove in descending index order so the remaining indices stay valid
    for (int i = uncachedJobs.size() - 1; i >= 0; i--) {
      final int uncachedIndex = uncachedJobs.get(i);
      batch.jobs().remove(uncachedIndex);
      batch.jobKeys().remove(uncachedIndex);
    }
    return new Preparation(values, pendingJobs, missingSecrets);
  }

  /**
   * Injects the cached values into the secret placeholders of the prepared jobs, replacing the
   * variables of the response batch in place. Errors are contained per job (the job keeps its
   * placeholders) and only logged, so no secret-related data can end up in persisted records.
   *
   * <p>The collector sized the batch against the max message size with {@link
   * EngineConfiguration#BATCH_SIZE_CALCULATION_BUFFER} bytes of slack, which the injected values
   * may consume. The first job whose values would grow the response further is dropped together
   * with every job after it, from the response batch and the to-be-activated batch alike (both must
   * still contain the same jobs), so the dropped jobs stay activatable and the next activation,
   * with a fresh budget, picks them up right away. Must run before the ACTIVATED event is appended.
   * Returns the dropped job when its values can never fit, for an incident.
   */
  public Optional<OversizedJob> injectSecretValues(
      final JobBatchRecord responseBatch,
      final JobBatchRecord activatedBatch,
      final Preparation preparation) {
    int remainingGrowth = EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
    for (final PendingJob pendingJob : preparation.pendingJobs()) {
      final byte[] injected = injectedVariablesOf(pendingJob, preparation.values());
      if (injected == null) {
        continue;
      }
      final int growth = injected.length - pendingJob.job().getVariablesBuffer().capacity();
      if (growth > remainingGrowth) {
        return dropJobsThatNoLongerFit(
            responseBatch, activatedBatch, pendingJob.index(), growth, remainingGrowth);
      }
      // the pending job belongs to the to-be-activated batch; the response element at the same
      // index carries the same variables until they are replaced here
      responseBatch.jobs().get(pendingJob.index()).setVariables(BufferUtil.wrapArray(injected));
      remainingGrowth -= growth;
    }
    return Optional.empty();
  }

  private Map<SecretReference, String> resolve(final Set<SecretReference> references) {
    try {
      return secretResolver.resolve(references);
    } catch (final RuntimeException e) {
      LOGGER.warn(
          "Failed to look up the {} secret reference(s) of a job activation batch; "
              + "the affected jobs are not activated",
          references.size(),
          e);
      return Map.of();
    }
  }

  /** Returns the job's distinct references without a cached value, in first-seen order. */
  private static List<SecretReference> uncachedReferences(
      final PendingJob pendingJob, final Map<SecretReference, String> values) {
    final List<SecretReference> uncached = new ArrayList<>();
    for (final Secret secret : pendingJob.secrets()) {
      final SecretReference reference = secret.reference();
      if (!values.containsKey(reference) && !uncached.contains(reference)) {
        uncached.add(reference);
      }
    }
    return uncached;
  }

  /**
   * Returns the pending job's variables with every secret placeholder replaced by its cached value,
   * or {@code null} when no placeholder was found. A failed injection also returns {@code null} and
   * is only logged, so the job is activated with its placeholders instead of failing the batch.
   */
  private byte[] injectedVariablesOf(
      final PendingJob pendingJob, final Map<SecretReference, String> values) {
    try {
      final DirectBuffer variables = pendingJob.job().getVariablesBuffer();
      final JsonNode document = variablesMapper.readTree(new DirectBufferInputStream(variables));
      boolean changed = false;
      for (final Secret secret : pendingJob.secrets()) {
        changed |= replaceInLeaf(document, secret, values.get(secret.reference()));
      }
      return changed ? variablesMapper.writeValueAsBytes(document) : null;
    } catch (final Exception e) {
      LOGGER.warn(
          "Failed to inject secret values into the variables of the job of element '{}' of "
              + "process instance {}; the job keeps its placeholders",
          pendingJob.job().getElementId(),
          pendingJob.job().getProcessInstanceKey(),
          e);
      return null;
    }
  }

  /**
   * Drops the job at the given index and every job after it from both batches, so none of them are
   * activated and all stay activatable, and marks both batches as truncated so the client polls for
   * the dropped jobs right away. A job dropped as the first of the batch had the full growth budget
   * to itself, so its values can never fit any batch: it is returned so the caller can raise an
   * incident for it, just like for a job that is too large to activate without secrets.
   */
  private static Optional<OversizedJob> dropJobsThatNoLongerFit(
      final JobBatchRecord responseBatch,
      final JobBatchRecord activatedBatch,
      final int index,
      final int growth,
      final int remainingGrowth) {
    JobRecord oversizedJob = null;
    long oversizedJobKey = -1;
    for (int i = activatedBatch.jobs().size() - 1; i >= index; i--) {
      responseBatch.jobs().remove(i);
      responseBatch.jobKeys().remove(i);
      oversizedJob = activatedBatch.jobs().remove(i);
      oversizedJobKey = activatedBatch.jobKeys().remove(i).getValue();
    }
    responseBatch.setTruncated(true);
    activatedBatch.setTruncated(true);
    LOGGER.warn(
        "Not activating the job of element '{}' of process instance {} and the jobs after it in "
            + "the batch: injecting the job's secret values would grow the activation batch by {} "
            + "bytes but only {} bytes remain before the response could exceed the max message "
            + "size. The dropped jobs stay activatable",
        oversizedJob.getElementId(),
        oversizedJob.getProcessInstanceKey(),
        growth,
        remainingGrowth);
    return index == 0
        ? Optional.of(new OversizedJob(oversizedJobKey, oversizedJob, growth))
        : Optional.empty();
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

  /** Collects the jobs with secret references, materializing each job's secrets exactly once. */
  private static List<PendingJob> collectPendingJobs(final JobBatchRecord batch) {
    final List<PendingJob> pendingJobs = new ArrayList<>();
    int index = 0;
    for (final JobRecord job : batch.jobs()) {
      if (job.hasSecretReferences()) {
        final long jobKey = batch.jobKeys().get(index).getValue();
        pendingJobs.add(new PendingJob(index, jobKey, job, secretsOf(job)));
      }
      index++;
    }
    return pendingJobs;
  }

  private static Set<SecretReference> referencesOf(final List<PendingJob> pendingJobs) {
    final Set<SecretReference> references = new LinkedHashSet<>();
    for (final PendingJob pendingJob : pendingJobs) {
      for (final Secret secret : pendingJob.secrets()) {
        references.add(secret.reference());
      }
    }
    return references;
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

  private record Secret(SecretReference reference, String path, String placeholder) {}

  /**
   * The preparation of an activation batch produced by {@link #removeJobsWithUncachedSecrets}: the
   * cached secret values, the surviving jobs with secret references (each with its secrets
   * materialized once) for {@link #injectSecretValues}, and the uncached references of the removed
   * jobs grouped with the keys of the jobs that await them, for the caller to request their
   * background resolution.
   */
  public record Preparation(
      Map<SecretReference, String> values,
      List<PendingJob> pendingJobs,
      Map<SecretReference, List<Long>> missingSecrets) {
    static final Preparation NONE = new Preparation(Map.of(), List.of(), Map.of());
  }

  /** A job with secret references: its index in the batch, its key, the job, and its secrets. */
  record PendingJob(int index, long jobKey, JobRecord job, List<Secret> secrets) {
    private PendingJob atIndex(final int index) {
      return new PendingJob(index, jobKey, job, secrets);
    }
  }

  /**
   * A job dropped from the batch whose secret values can never fit: injecting them would grow the
   * batch by {@code growth} bytes, more than the whole budget any activation batch has to spare.
   */
  public record OversizedJob(long jobKey, JobRecord job, int growth) {}
}
