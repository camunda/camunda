/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
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

  public static List<GeneratedVariableSearchStrictContract> toSearchProjections(
      final List<VariableEntity> variableEntities, final boolean truncateValues) {
    return variableEntities.stream()
        .map(entity -> toSearchProjection(entity, truncateValues))
        .toList();
  }

  public static GeneratedVariableSearchStrictContract toSearchProjection(
      final VariableEntity entity, final boolean truncateValues) {
    return GeneratedVariableSearchStrictContract.builder()
        .name(
            ContractPolicy.requireNonNull(
                entity.name(), GeneratedVariableSearchStrictContract.Fields.NAME, entity))
        .tenantId(
            ContractPolicy.requireNonNull(
                entity.tenantId(), GeneratedVariableSearchStrictContract.Fields.TENANT_ID, entity))
        .variableKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.variableKey()),
                GeneratedVariableSearchStrictContract.Fields.VARIABLE_KEY,
                entity))
        .scopeKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.scopeKey()),
                GeneratedVariableSearchStrictContract.Fields.SCOPE_KEY,
                entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()),
                GeneratedVariableSearchStrictContract.Fields.PROCESS_INSTANCE_KEY,
                entity))
        .value(
            ContractPolicy.requireNonNull(
                !truncateValues
                    ? ContractPolicy.resolvePreviewValue(
                        entity.value(), entity.fullValue(), entity.isPreview())
                    : entity.value(),
                GeneratedVariableSearchStrictContract.Fields.VALUE,
                entity))
        .isTruncated(
            ContractPolicy.requireNonNull(
                truncateValues && entity.isPreview(),
                GeneratedVariableSearchStrictContract.Fields.IS_TRUNCATED,
                entity))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .build();
  }

  public static GeneratedVariableStrictContract toItemProjection(final VariableEntity entity) {
    return GeneratedVariableStrictContract.builder()
        .name(
            ContractPolicy.requireNonNull(
                entity.name(), GeneratedVariableStrictContract.Fields.NAME, entity))
        .tenantId(
            ContractPolicy.requireNonNull(
                entity.tenantId(), GeneratedVariableStrictContract.Fields.TENANT_ID, entity))
        .variableKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.variableKey()),
                GeneratedVariableStrictContract.Fields.VARIABLE_KEY,
                entity))
        .scopeKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.scopeKey()),
                GeneratedVariableStrictContract.Fields.SCOPE_KEY,
                entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()),
                GeneratedVariableStrictContract.Fields.PROCESS_INSTANCE_KEY,
                entity))
        .value(
            ContractPolicy.requireNonNull(
                ContractPolicy.resolvePreviewValue(
                    entity.value(), entity.fullValue(), entity.isPreview()),
                GeneratedVariableStrictContract.Fields.VALUE,
                entity))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .build();
  }
}
