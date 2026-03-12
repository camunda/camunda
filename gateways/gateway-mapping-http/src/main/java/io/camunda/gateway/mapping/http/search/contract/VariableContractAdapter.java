/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableResultBaseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.VariableResult;
import io.camunda.gateway.protocol.model.VariableSearchResult;
import io.camunda.search.entities.VariableEntity;
import java.util.List;

/**
 * Contract adaptation layer for variable projections.
 *
 * <p>Policy in this adapter controls whether values are emitted as previews or full payloads based
 * on the operation context.
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
      final VariableEntity variableEntity, final boolean truncateValues) {
    final var strictBase = toStrictBase(variableEntity);
    final var strictSearch = toStrictSearch(variableEntity, truncateValues);
    return new VariableSearchResult()
        .variableKey(strictBase.variableKey())
        .name(strictBase.name())
        .value(strictSearch.value())
        .processInstanceKey(strictBase.processInstanceKey())
        .rootProcessInstanceKey(strictBase.rootProcessInstanceKey())
        .tenantId(strictBase.tenantId())
        .isTruncated(strictSearch.isTruncated())
        .scopeKey(strictBase.scopeKey());
  }

  public static VariableResult toItemProjection(final VariableEntity variableEntity) {
    final var strictBase = toStrictBase(variableEntity);
    final var strictItem = toStrictItem(variableEntity);
    return new VariableResult()
        .variableKey(strictBase.variableKey())
        .name(strictBase.name())
        .value(strictItem.value())
        .processInstanceKey(strictBase.processInstanceKey())
        .rootProcessInstanceKey(strictBase.rootProcessInstanceKey())
        .tenantId(strictBase.tenantId())
        .scopeKey(strictBase.scopeKey());
  }

  private static GeneratedVariableResultBaseStrictContract toStrictBase(
      final VariableEntity variableEntity) {
    return GeneratedVariableResultBaseStrictContract.builder()
        .name(variableEntity.name(), ContractPolicy.requiredNonNull())
        .tenantId(variableEntity.tenantId(), ContractPolicy.requiredNonNull())
        .variableKey(variableEntity.variableKey(), ContractPolicy.requiredNonNull())
        .scopeKey(variableEntity.scopeKey(), ContractPolicy.requiredNonNull())
        .processInstanceKey(variableEntity.processInstanceKey(), ContractPolicy.requiredNonNull())
        .rootProcessInstanceKey(variableEntity.rootProcessInstanceKey())
        .build();
  }

  private static GeneratedVariableSearchStrictContract toStrictSearch(
      final VariableEntity variableEntity, final boolean truncateValues) {
    return GeneratedVariableSearchStrictContract.builder()
        .value(
            !truncateValues
                ? ContractPolicy.resolvePreviewValue(
                    variableEntity.value(), variableEntity.fullValue(), variableEntity.isPreview())
                : variableEntity.value(),
            ContractPolicy.requiredNonNull())
        .isTruncated(truncateValues && variableEntity.isPreview(), ContractPolicy.requiredNonNull())
        .build();
  }

  private static GeneratedVariableStrictContract toStrictItem(final VariableEntity variableEntity) {
    return GeneratedVariableStrictContract.builder()
        .value(
            ContractPolicy.resolvePreviewValue(
                variableEntity.value(), variableEntity.fullValue(), variableEntity.isPreview()),
            ContractPolicy.requiredNonNull())
        .build();
  }
}
