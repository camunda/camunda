/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.VariableContract;
import io.camunda.gateway.mapping.http.search.contract.generated.VariableSearchContract;
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

  public static List<VariableSearchContract> toSearchProjections(
      final List<VariableEntity> variableEntities, final boolean truncateValues) {
    return variableEntities.stream()
        .map(entity -> toSearchProjection(entity, truncateValues))
        .toList();
  }

  public static VariableSearchContract toSearchProjection(
      final VariableEntity entity, final boolean truncateValues) {
    return VariableSearchContract.builder()
        .name(
            ContractPolicy.requireNonNull(
                entity.name(), VariableSearchContract.Fields.NAME, entity))
        .tenantId(
            ContractPolicy.requireNonNull(
                entity.tenantId(), VariableSearchContract.Fields.TENANT_ID, entity))
        .variableKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.variableKey()),
                VariableSearchContract.Fields.VARIABLE_KEY,
                entity))
        .scopeKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.scopeKey()),
                VariableSearchContract.Fields.SCOPE_KEY,
                entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()),
                VariableSearchContract.Fields.PROCESS_INSTANCE_KEY,
                entity))
        .value(
            ContractPolicy.requireNonNull(
                !truncateValues
                    ? ContractPolicy.resolvePreviewValue(
                        entity.value(), entity.fullValue(), entity.isPreview())
                    : entity.value(),
                VariableSearchContract.Fields.VALUE,
                entity))
        .isTruncated(
            ContractPolicy.requireNonNull(
                truncateValues && entity.isPreview(),
                VariableSearchContract.Fields.IS_TRUNCATED,
                entity))
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .build();
  }

  public static VariableContract toItemProjection(final VariableEntity entity) {
    return VariableContract.builder()
        .name(ContractPolicy.requireNonNull(entity.name(), VariableContract.Fields.NAME, entity))
        .tenantId(
            ContractPolicy.requireNonNull(
                entity.tenantId(), VariableContract.Fields.TENANT_ID, entity))
        .variableKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.variableKey()),
                VariableContract.Fields.VARIABLE_KEY,
                entity))
        .scopeKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.scopeKey()), VariableContract.Fields.SCOPE_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()),
                VariableContract.Fields.PROCESS_INSTANCE_KEY,
                entity))
        .value(
            ContractPolicy.requireNonNull(
                ContractPolicy.resolvePreviewValue(
                    entity.value(), entity.fullValue(), entity.isPreview()),
                VariableContract.Fields.VALUE,
                entity))
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .build();
  }
}
