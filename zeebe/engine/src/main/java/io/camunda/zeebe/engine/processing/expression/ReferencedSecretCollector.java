/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.clustervariable.ClusterVariableInstance;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Accumulates, for a single expression evaluation, the {@code camunda.secrets.<name>} references
 * that were resolved from <em>trusted</em> sources: a reference used directly in the expression
 * (see {@link SecretReferenceEvaluationContext}) or one carried by a {@code SECRET_REFERENCE}-kind
 * cluster variable the expression read (see {@code TenantScopeClusterVariableEvaluationContext} and
 * {@code GlobalScopeClusterVariableEvaluationContext}).
 *
 * <p>This collector is the single authority on what counts as a trusted reference: references that
 * appear in request-body variables or plain (JSON-kind) cluster variables never reach it, so
 * callers can safely resolve exactly the references it reports without reintroducing a
 * secret-injection vector.
 *
 * <p>Only the expression-endpoint path ({@code ExpressionBehavior}) wires a collector into its
 * evaluation contexts; the BPMN input-mapping contexts are built without one and never record. The
 * engine's stream processing is single-threaded per partition, so a single mutable instance is
 * reused across endpoint evaluations: {@code ExpressionBehavior} {@link #reset() resets} it before
 * each evaluation and {@link #drain() drains} it afterwards.
 */
@NullMarked
public final class ReferencedSecretCollector {

  private final Set<ReferencedSecret> references = new LinkedHashSet<>();

  /** Records a reference used directly in the expression (against the given store). */
  public void add(final String storeId, final String secretReference) {
    references.add(new ReferencedSecret(storeId, secretReference));
  }

  /**
   * Records the references a resolved cluster variable brought into the evaluation, but only when
   * its kind is {@link ClusterVariableKind#SECRET_REFERENCE}. A JSON-kind variable contributes
   * nothing, even if its value happens to contain a {@code camunda.secrets.<name>}-looking string:
   * only references the engine detected and pinned at write time are trusted.
   */
  public void addClusterVariableReferences(final ClusterVariableInstance instance) {
    if (instance.getRecord().getKind() != ClusterVariableKind.SECRET_REFERENCE) {
      return;
    }
    instance
        .getRecord()
        .getSecretReferences()
        .forEach(reference -> add(reference.getStoreId(), reference.getSecretReference()));
  }

  /** Starts a fresh collection, discarding anything a previous evaluation left behind. */
  public void reset() {
    references.clear();
  }

  /**
   * Returns a snapshot of the collected references (insertion order, deduplicated) and clears them.
   */
  public List<ReferencedSecret> drain() {
    final var snapshot = List.copyOf(references);
    references.clear();
    return snapshot;
  }

  public record ReferencedSecret(String storeId, String secretReference) {}
}
