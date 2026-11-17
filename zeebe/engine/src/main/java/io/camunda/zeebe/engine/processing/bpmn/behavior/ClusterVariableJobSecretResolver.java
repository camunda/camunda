/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import com.fasterxml.jackson.core.JsonPointer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.state.clustervariable.ClusterVariableInstance;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the cluster-variable references detected in an input mapping (see {@link
 * ClusterVariableReference}) against the current cluster-variable state, folding the secret
 * references of any {@code SECRET_REFERENCE}-kind hit into the same shape as {@link
 * SecretReference}, so {@code BpmnJobBehavior} can merge them with an element's direct secret
 * references before writing the job-created event.
 *
 * <p>Scope resolution mirrors the FEEL runtime's {@code camunda.vars.<scope>.<name>} evaluation
 * (see {@code BpmnBehaviorsImpl}): {@code tenant} resolves at tenant scope only, {@code cluster} at
 * global scope only, {@code env} at tenant scope falling back to global scope. A missing variable,
 * or one whose kind is {@code JSON}, contributes nothing — never an incident.
 */
public final class ClusterVariableJobSecretResolver {

  private final ClusterVariableState clusterVariableState;

  public ClusterVariableJobSecretResolver(final ClusterVariableState clusterVariableState) {
    this.clusterVariableState = clusterVariableState;
  }

  public Map<String, Set<SecretReference>> resolve(
      final Map<String, Set<ClusterVariableReference>> clusterVariableReferences,
      final String tenantId) {
    final var result = new LinkedHashMap<String, Set<SecretReference>>();
    resolveInto(clusterVariableReferences, tenantId, result);
    return result;
  }

  /**
   * Folds resolved secret references directly into a caller-supplied {@code target} map, avoiding
   * the extra map allocation and copy pass that {@link #resolve} would otherwise require of a
   * caller that already has its own map to merge into (see {@code BpmnJobBehavior}).
   */
  public void resolveInto(
      final Map<String, Set<ClusterVariableReference>> clusterVariableReferences,
      final String tenantId,
      final Map<String, Set<SecretReference>> target) {
    clusterVariableReferences.forEach(
        (leafPointer, references) ->
            references.forEach(reference -> resolveOne(leafPointer, reference, tenantId, target)));
  }

  private void resolveOne(
      final String leafPointer,
      final ClusterVariableReference reference,
      final String tenantId,
      final Map<String, Set<SecretReference>> result) {
    resolveInstance(reference.scope(), reference.name(), tenantId)
        .filter(instance -> instance.getRecord().getKind() == ClusterVariableKind.SECRET_REFERENCE)
        .ifPresent(
            instance ->
                instance
                    .getRecord()
                    .getSecretReferences()
                    .forEach(secretRef -> fold(leafPointer, reference, secretRef, result)));
  }

  private void fold(
      final String leafPointer,
      final ClusterVariableReference reference,
      final ClusterVariableSecretReferenceValue secretRef,
      final Map<String, Set<SecretReference>> result) {
    rebase(secretRef.getPath(), reference.fieldPath())
        .ifPresent(
            rebasedPointer ->
                result
                    .computeIfAbsent(leafPointer + rebasedPointer, key -> new LinkedHashSet<>())
                    .add(
                        new SecretReference(
                            secretRef.getStoreId(), secretRef.getSecretReference())));
  }

  private Optional<ClusterVariableInstance> resolveInstance(
      final String scope, final String name, final String tenantId) {
    final var nameBuffer = BufferUtil.wrapString(name);
    return switch (scope) {
      case "tenant" -> clusterVariableState.getTenantScopedClusterVariable(nameBuffer, tenantId);
      case "cluster" -> clusterVariableState.getGloballyScopedClusterVariable(nameBuffer);
      case "env" ->
          clusterVariableState
              .getTenantScopedClusterVariable(nameBuffer, tenantId)
              .filter(instance -> instance.getValueBuffer().capacity() > 0)
              .or(() -> clusterVariableState.getGloballyScopedClusterVariable(nameBuffer));
      default -> Optional.empty();
    };
  }

  /**
   * Rebases a stored secret's RFC 6901 pointer against the field-path segments a cluster-variable
   * reference accesses (e.g. {@code camunda.vars.env.myVar.a.b} has field path {@code [a, b]}).
   * Compares pointer segments structurally via {@link JsonPointer#matchProperty(String)} rather
   * than string-prefixing, so a field path {@code [a]} does not wrongly match a stored pointer
   * {@code /ab/token}. Returns empty when the secret lies outside the accessed sub-value — the
   * mapped value never contains it, so there is nothing to fold.
   */
  private static Optional<String> rebase(final String storedPath, final List<String> fieldPath) {
    var remaining = JsonPointer.compile(storedPath);
    for (final String segment : fieldPath) {
      remaining = remaining.matchProperty(segment);
      if (remaining == null) {
        return Optional.empty();
      }
    }
    return Optional.of(remaining.toString());
  }
}
