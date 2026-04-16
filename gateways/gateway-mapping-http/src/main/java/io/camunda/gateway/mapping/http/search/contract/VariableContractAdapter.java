/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.resolvePreviewValue;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;

import io.camunda.gateway.protocol.model.VariableResult;
import io.camunda.gateway.protocol.model.VariableSearchResult;
import io.camunda.search.entities.VariableEntity;
import java.util.List;

/**
 * Contract adaptation layer for variable projections.
 *
 * <p>Policy in this adapter controls whether values are emitted as previews or full payloads based
 * on the operation context. The generated DTOs flatten the OpenAPI allOf inheritance so each
 * projection is a self-contained record that Jackson can serialize directly.
 */
public final class VariableContractAdapter {

  private VariableContractAdapter() {}

  public static List<VariableSearchResult> toSearchProjections(
      final List<VariableEntity> variableEntities, final boolean truncateValues) {
    return variableEntities.stream()
        .map(entity -> toSearchProjection(entity, truncateValues))
        .toList();
  }

  public static VariableSearchResult toSearchProjection(
      final VariableEntity entity, final boolean truncateValues) {
    return new VariableSearchResult()
        .name(requireNonNull(entity.name(), "name", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .variableKey(requireNonNull(keyToString(entity.variableKey()), "variableKey", entity))
        .scopeKey(requireNonNull(keyToString(entity.scopeKey()), "scopeKey", entity))
        .processInstanceKey(
            requireNonNull(keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .value(
            requireNonNull(
                !truncateValues
                    ? resolvePreviewValue(entity.value(), entity.fullValue(), entity.isPreview())
                    : entity.value(),
                "value",
                entity))
        .isTruncated(requireNonNull(truncateValues && entity.isPreview(), "isTruncated", entity))
        .rootProcessInstanceKey(keyToString(entity.rootProcessInstanceKey()));
  }

  public static VariableResult toItemProjection(final VariableEntity entity) {
    return new VariableResult()
        .name(requireNonNull(entity.name(), "name", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .variableKey(requireNonNull(keyToString(entity.variableKey()), "variableKey", entity))
        .scopeKey(requireNonNull(keyToString(entity.scopeKey()), "scopeKey", entity))
        .processInstanceKey(
            requireNonNull(keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .value(
            requireNonNull(
                resolvePreviewValue(entity.value(), entity.fullValue(), entity.isPreview()),
                "value",
                entity))
        .rootProcessInstanceKey(keyToString(entity.rootProcessInstanceKey()));
  }
}
