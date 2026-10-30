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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.jspecify.annotations.Nullable;
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
 * <p>A checked reference means a secret is *possibly* referenced by the evaluated expression, not
 * that one certainly is: FEEL evaluates only one branch of a conditional, so detection records a
 * reference for every branch at the same pointer. A reference that finds no placeholder therefore
 * fails the injection only when placeholder-shaped text survives at its path once every reference
 * has been attempted - a sibling reference at the same path may be the one that resolves it.
 *
 * <p>The replacement is textual: every occurrence of the placeholder in the addressed leaf is
 * replaced, including text that merely spells out the placeholder next to a real reference at the
 * same path. This can only surface a secret in a leaf that already receives that secret. The
 * residual scan for a surviving placeholder reads the injected result rather than the original
 * text, so a secret value that itself contains placeholder-shaped text would fail closed when
 * another reference at the same path triggers the check - a lone reference that replaces its own
 * placeholder cleanly is never rescanned.
 */
public final class JobSecretLookup {

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
   * Returns the job's variables with every secret placeholder replaced by its cached value, or
   * {@code null} when nothing was replaced. A reference that finds no placeholder is only a failure
   * when placeholder-shaped text survives at its path. That check runs once per distinct path,
   * after every reference has been attempted, because a sibling reference at the same path may be
   * the one that resolves it.
   *
   * <p>Only the secrets of a {@link #check(JobRecord)} of the same job may be passed in; a value
   * read for another job is not addressed by this job's paths.
   */
  public byte[] injectedVariablesOf(final JobRecord job, final List<CachedSecret> cachedSecrets)
      throws IOException {
    final DirectBuffer variables = job.getVariablesBuffer();
    final JsonNode document = variablesMapper.readTree(new DirectBufferInputStream(variables));
    boolean changed = false;
    // keyed by path and filled with putIfAbsent so a FEEL conditional's branches, which all
    // register their reference at the same path, are scanned only once
    final var unresolved = new LinkedHashMap<String, Secret>();
    for (final CachedSecret cachedSecret : cachedSecrets) {
      final LeafResult result = replaceInLeaf(document, cachedSecret);
      if (result.replaced()) {
        changed = true;
      } else if (result.unresolvedNode() != null) {
        unresolved.putIfAbsent(cachedSecret.secret().path(), cachedSecret.secret());
      }
    }
    if (!unresolved.isEmpty()) {
      failIfAnyPlaceholderSurvives(document, unresolved.values());
    }
    // an unchanged job must keep its original encoding and stay out of any growth budget
    return changed ? variablesMapper.writeValueAsBytes(document) : null;
  }

  /**
   * Throws when placeholder-shaped text survives at the path of a reference that found no
   * placeholder of its own, scanning each distinct path once. Reads the document fresh for each one
   * rather than a node captured during the loop above: a sibling reference at the same path can
   * still replace the placeholder after this secret's own attempt found nothing, and a text node is
   * immutable, so a node captured mid-loop would never reflect that later replacement - the outcome
   * would then depend on which reference is attempted first.
   */
  private static void failIfAnyPlaceholderSurvives(
      final JsonNode document, final Collection<Secret> unresolved) {
    for (final Secret secret : unresolved) {
      final JsonNode node = deepestExisting(document, JsonPointer.compile(secret.path())).node();
      if (node != null && holdsPlaceholder(node)) {
        throw new SecretPointerMismatchException(secret.path(), secret.placeholder());
      }
    }
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
   * Replaces the placeholder in the text leaf addressed by the secret's JSON pointer. Returns a
   * replaced result when it did, an absent result when nothing of ours is at that pointer at all
   * (an unset key, e.g. because the worker's fetchVariables excluded it), or an unresolved result
   * carrying the node the caller must scan for a surviving placeholder — the leaf, or the scalar or
   * array the pointer tried to descend into.
   *
   * <p>Nothing is thrown here: whether a non-replacement is a failure depends on what the other
   * references at the same path do, which is only known once they have all been attempted.
   */
  private static LeafResult replaceInLeaf(
      final JsonNode document, final CachedSecret cachedSecret) {
    final Secret secret = cachedSecret.secret();
    if (!secret.path().startsWith("/")) {
      return LeafResult.ABSENT;
    }
    final JsonPointer pointer = JsonPointer.compile(secret.path());
    final Walked walked = deepestExisting(document, pointer);
    final JsonNode stopped = walked.node();
    if (stopped == null) {
      // the walk stopped on a container that simply lacks our key: our slot is unset, so there is
      // nothing to replace and nothing that could have been left behind
      return LeafResult.ABSENT;
    }
    if (!stopped.isTextual() || walked.parent() == null) {
      // either a container or array at the leaf, a scalar the pointer tried to descend into, or a
      // text leaf whose immediate parent isn't a genuine object property and so can't be written to
      return LeafResult.unresolved(stopped);
    }
    final String text = stopped.textValue();
    final String replaced = text.replace(secret.placeholder(), cachedSecret.value());
    if (replaced.equals(text)) {
      return LeafResult.unresolved(stopped);
    }
    walked.parent().put(pointer.last().getMatchingProperty(), replaced);
    return LeafResult.REPLACED;
  }

  /**
   * Walks the pointer segment by segment from the document root, returning the deepest existing
   * node it reaches together with the object that holds it - captured during the same walk instead
   * of re-resolving the pointer's head afterwards. The node is {@code null} when an object simply
   * doesn't hold the next key: our slot is legitimately unset, e.g. because the worker's
   * fetchVariables excluded it. An array is different: the cluster-variable scanner does produce
   * array-index pointers, for a secret nested inside a {@code SECRET_REFERENCE} variable's list
   * (see {@code ClusterVariableSecretReferenceScanner}), and injection can only write into an
   * object via {@code ObjectNode.put} - it can never write into an array, in range or not. A
   * missing array index is therefore not read as absent; the array itself is returned so the caller
   * fails closed on it, exactly like an in-range index whose element is reached but sits one level
   * below an array rather than an object (see the {@code parent} check below). The parent is {@code
   * null} whenever the node isn't reached through a genuine object property one segment up - a
   * scalar or array immediately above it can't be written back to by key. Walking segment by
   * segment - rather than asking {@link JsonNode#at} for the leaf and its parent separately - is
   * what makes tracking both possible in one pass, at a cost of one key lookup per segment.
   */
  private static Walked deepestExisting(final JsonNode document, final JsonPointer pointer) {
    JsonNode node = document;
    // the object directly above `node`, or null when `node` sits under a scalar or array and so
    // can't be written back to by key; recomputed on each descent
    ObjectNode writableParent = null;
    for (JsonPointer remaining = pointer; !remaining.matches(); remaining = remaining.tail()) {
      if (!node.isContainerNode()) {
        // a scalar occupies this path already; the remaining segments have nothing to descend into
        return new Walked(node, null);
      }
      writableParent = node.isObject() ? (ObjectNode) node : null;
      final JsonNode child = childOf(node, remaining);
      if (child == null) {
        // an object missing the key is unset; an array is never writable, whether or not the
        // index exists - fail closed by scanning the array itself instead of treating it as absent
        return new Walked(node.isArray() ? node : null, null);
      }
      node = child;
    }
    return new Walked(node, writableParent);
  }

  /**
   * The child the pointer's leading segment selects from {@code container}: by index when it is an
   * array, by property name when it is an object. {@code null} when no such child exists.
   */
  private static @Nullable JsonNode childOf(final JsonNode container, final JsonPointer segment) {
    return container.isArray()
        ? container.get(segment.getMatchingIndex())
        : container.get(segment.getMatchingProperty());
  }

  /**
   * True when the node still holds text that looks like an unresolved {@code
   * camunda.secrets.<name>} placeholder. Reads the node *after* every replacement, so a placeholder
   * that resolved is already overwritten by its value and cannot match — which also avoids
   * tokenising the original text, something adjacent placeholders do not survive (the name class is
   * greedy, so {@code camunda.secrets.a} followed directly by {@code camunda.secrets.b} reads as
   * one token). A container is descended, since that is the whole reason it is a failure.
   */
  private static boolean holdsPlaceholder(final JsonNode node) {
    if (node.isTextual()) {
      return SecretReference.REFERENCE_PATTERN.matcher(node.textValue()).find();
    }
    if (node.isContainerNode()) {
      for (final JsonNode child : node) {
        if (holdsPlaceholder(child)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Reads the job's secret references, longest placeholder first so a reference name that is a
   * prefix of another (e.g. {@code token} vs {@code token2}) cannot corrupt the longer placeholder
   * when both are injected into the same leaf.
   */
  private static List<Secret> secretsOf(final JobRecord job) {
    final List<Secret> secrets = new ArrayList<>();
    for (final JobSecretReference reference : job.secretReferences()) {
      final var secretReference =
          new SecretReference(reference.getStoreId(), reference.getSecretReference());
      secrets.add(new Secret(secretReference, reference.getPath(), secretReference.reference()));
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

  /**
   * Thrown by {@link JobSecretLookup#injectedVariablesOf} when a secret reference's JSON pointer
   * exists but doesn't address a text leaf of an object, or addresses one whose content doesn't
   * contain the expected placeholder, and no sibling reference at the same path resolved it either.
   * Both activation paths inspect {@link #path()} and {@link #placeholder()} to name the mismatch
   * in the {@code SECRET_RESOLUTION_ERROR} incident they share ({@link
   * JobSecretInjectionIncident}): the batch path via {@link JobSecretInjector}, the job-push path
   * in {@code BpmnJobActivationBehavior}, which is why this is public. Only the placeholder and
   * JSON pointer are ever read out; this exception's own message is log-only and never persisted,
   * since it may quote the variables document.
   */
  public static final class SecretPointerMismatchException extends RuntimeException {
    private final String path;
    private final String placeholder;

    SecretPointerMismatchException(final String path, final String placeholder) {
      super(
          "Secret reference '"
              + placeholder
              + "' at '"
              + path
              + "' does not address a text leaf of the job's variables, or its content no longer "
              + "matches the placeholder - the referenced value or its containing structure "
              + "changed since the placeholder was baked in (job variables are re-read from the "
              + "current variable scope on every activation attempt, so a later merge into that "
              + "scope can overwrite it), or the mapping that produced it never addresses a "
              + "single text value in the first place.");
      this.path = path;
      this.placeholder = placeholder;
    }

    public String path() {
      return path;
    }

    public String placeholder() {
      return placeholder;
    }
  }

  /**
   * The outcome of {@link #replaceInLeaf}: whether the placeholder was replaced, and - when it
   * wasn't - the node the caller must scan for a surviving one, {@code null} when nothing of ours
   * is at the pointer at all. A boolean flag rather than a sentinel node: Jackson caches small
   * nodes like {@code BooleanNode.TRUE}, so a real leaf holding that same value could otherwise
   * collide with an identity-based "was it replaced" signal.
   */
  private record LeafResult(boolean replaced, @Nullable JsonNode unresolvedNode) {
    private static final LeafResult REPLACED = new LeafResult(true, null);
    private static final LeafResult ABSENT = new LeafResult(false, null);

    private static LeafResult unresolved(final JsonNode node) {
      return new LeafResult(false, node);
    }
  }

  /**
   * The result of {@link #deepestExisting}: the deepest node a pointer walk reaches, and - only
   * when that node is reached through a genuine object property - the object holding it.
   */
  private record Walked(@Nullable JsonNode node, @Nullable ObjectNode parent) {}
}
