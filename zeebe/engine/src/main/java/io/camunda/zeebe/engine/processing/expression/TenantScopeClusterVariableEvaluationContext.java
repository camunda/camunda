/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.engine.state.clustervariable.ClusterVariableInstance;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

public final class TenantScopeClusterVariableEvaluationContext implements ScopedEvaluationContext {

  private final ClusterVariableState clusterVariableState;
  private final String tenantId;
  private final @Nullable ReferencedSecretCollector referencedSecretCollector;

  public TenantScopeClusterVariableEvaluationContext(
      final ClusterVariableState clusterVariableState,
      final @Nullable ReferencedSecretCollector referencedSecretCollector) {
    this(clusterVariableState, "", referencedSecretCollector);
  }

  private TenantScopeClusterVariableEvaluationContext(
      final ClusterVariableState clusterVariableState,
      final String tenantId,
      final @Nullable ReferencedSecretCollector referencedSecretCollector) {
    this.clusterVariableState = clusterVariableState;
    this.tenantId = tenantId;
    this.referencedSecretCollector = referencedSecretCollector;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    return Either.left(
        clusterVariableState
            .getTenantScopedClusterVariable(BufferUtil.wrapString(variableName), tenantId)
            .filter(instance -> instance.getValueBuffer().capacity() > 0)
            .map(this::recordSecretReferences)
            .map(ClusterVariableInstance::getValueBuffer)
            .orElse(null));
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    return new TenantScopeClusterVariableEvaluationContext(
        clusterVariableState, tenantId, referencedSecretCollector);
  }

  private ClusterVariableInstance recordSecretReferences(final ClusterVariableInstance instance) {
    if (referencedSecretCollector != null) {
      referencedSecretCollector.addClusterVariableReferences(instance);
    }
    return instance;
  }
}
