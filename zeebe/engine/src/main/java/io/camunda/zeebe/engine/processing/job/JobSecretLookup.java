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
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * Looks up the secret references of a job in what the stores of the {@link SecretStoreRegistry}
 * already hold locally, and injects the values it found into the job's variables. No store is read,
 * so this cannot block the stream processor. Shared by both activation paths: the long poll injects
 * into its activation response ({@link JobSecretInjector}), the job push into the job it streams
 * ({@code BpmnJobActivationBehavior}).
 *
 * <p>This holds no secret value of its own. {@link #check(JobRecord)} reads the value of every
 * reference it finds into the {@link SecretCheckResult} it returns, so the following {@link
 * #injectedVariablesOf(JobRecord, List)} cannot lose a value that was evicted from the cache in
 * between, and the values live no longer than the result the caller holds.
 *
 * <p>The replacement is textual: every occurrence of the placeholder in the addressed leaf is
 * replaced, including text that merely spells out the placeholder next to a real reference at the
 * same path. This can only surface a secret in a leaf that already receives that secret.
 */
public final class JobSecretLookup {

  private static final String SECRET_REFERENCE_PREFIX = "camunda.secrets.";

  /** Reads and writes the msgpack encoding of job variables as Jackson trees. */
  private final ObjectMapper variablesMapper = new ObjectMapper(new MessagePackFactory());

  private final SecretStoreRegistry secretStoreRegistry;

  public JobSecretLookup(final SecretStoreRegistry secretStoreRegistry) {
    this.secretStoreRegistry = secretStoreRegistry;
  }

  /**
   * Checks each secret reference of the job (stored on the {@link JobRecord} at creation) for a
   * value its store already holds locally, and returns the values found along with the references
   * that had none. No store is read, so this cannot block the stream processor. Every reference is
   * checked, even after the first miss, so the caller sees all non-cached references of the job. A
   * failing lookup propagates to the caller and fails the command being processed.
   */
  public SecretCheckResult check(final JobRecord job) {
    if (!job.hasSecretReferences()) {
      return SecretCheckResult.NO_SECRETS;
    }
    final List<CachedSecret> cachedSecrets = new ArrayList<>();
    final List<Secret> nonCachedSecrets = new ArrayList<>();
    for (final Secret secret : secretsOf(job)) {
      lookupLocal(secret.reference())
          .ifPresentOrElse(
              value -> cachedSecrets.add(new CachedSecret(secret, value)),
              () -> nonCachedSecrets.add(secret));
    }
    return new SecretCheckResult(cachedSecrets, nonCachedSecrets);
  }

  /**
   * Returns the job's variables with every secret placeholder replaced by the value read for it, or
   * {@code null} when no placeholder was found. A failure propagates to the caller, which must keep
   * the job from being activated and report it for an incident.
   *
   * <p>Only the secrets of a {@link #check(JobRecord)} of the same job may be passed in; a value
   * read for another job is not addressed by this job's paths.
   */
  public byte[] injectedVariablesOf(final JobRecord job, final List<CachedSecret> cachedSecrets)
      throws IOException {
    final DirectBuffer variables = job.getVariablesBuffer();
    final JsonNode document = variablesMapper.readTree(new DirectBufferInputStream(variables));
    // a placeholder can be absent (e.g. fetchVariables excluded the variable); an unchanged job
    // must keep its original encoding and stay out of any growth budget, so it returns null
    boolean changed = false;
    for (final CachedSecret cachedSecret : cachedSecrets) {
      changed |= replaceInLeaf(document, cachedSecret);
    }
    return changed ? variablesMapper.writeValueAsBytes(document) : null;
  }

  /**
   * Returns the value the reference's store holds for it locally, or empty when no store holds one.
   * The lookup reads no store, so it cannot block the stream processor; a lookup failure is
   * deliberately not caught here: it propagates and fails the command being processed.
   */
  private Optional<String> lookupLocal(final SecretReference reference) {
    // the store lookup is exact: a reference naming no configured store addresses none
    return Optional.ofNullable(secretStoreRegistry.getStores().get(reference.storeId()))
        .flatMap(store -> store.lookupLocal(reference.name()));
  }

  /**
   * Replaces the placeholder in the text leaf addressed by the secret's JSON pointer. Pointers that
   * do not address a text leaf of an object (e.g. the path no longer matches the variables, or it
   * runs into an array) are skipped defensively.
   */
  private static boolean replaceInLeaf(final JsonNode document, final CachedSecret cachedSecret) {
    final Secret secret = cachedSecret.secret();
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
    final String replaced = text.replace(secret.placeholder(), cachedSecret.value());
    if (replaced.equals(text)) {
      return false;
    }
    ((ObjectNode) parent).put(pointer.last().getMatchingProperty(), replaced);
    return true;
  }

  /**
   * Reads the job's secret references, longest placeholder first so a reference name that is a
   * prefix of another (e.g. {@code token} vs {@code token2}) cannot corrupt the longer placeholder
   * when both are injected into the same leaf.
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

  /**
   * The result of {@link #check(JobRecord)} for one job: the job's secrets with the value their
   * store held, and the secrets that had none (both empty for a job without secret references). A
   * job with any non-cached secret must not be activated.
   */
  public record SecretCheckResult(List<CachedSecret> cachedSecrets, List<Secret> nonCachedSecrets) {
    static final SecretCheckResult NO_SECRETS = new SecretCheckResult(List.of(), List.of());
  }

  /** One reference of a job: where its value comes from, and where it goes in the variables. */
  public record Secret(SecretReference reference, String path, String placeholder) {}

  /**
   * A job secret together with the value its store held when the job was checked. The value is only
   * ever injected into the record handed to the worker, so it lives as long as the check result the
   * caller holds and no longer.
   */
  public record CachedSecret(Secret secret, String value) {}
}
