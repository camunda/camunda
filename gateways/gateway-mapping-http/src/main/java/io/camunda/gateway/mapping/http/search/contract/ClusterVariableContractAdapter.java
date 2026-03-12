/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableResultBaseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.ClusterVariableResult;
import io.camunda.gateway.protocol.model.ClusterVariableScopeEnum;
import io.camunda.gateway.protocol.model.ClusterVariableSearchResult;
import io.camunda.search.entities.ClusterVariableEntity;
import java.util.List;

/**
 * Contract adaptation layer for cluster variable projections.
 *
 * <p>Policy in this adapter controls preview/full value emission and the response shape differences
 * between search and item projections.
 */
public final class ClusterVariableContractAdapter {

  private ClusterVariableContractAdapter() {}

  public static List<ClusterVariableSearchResult> toSearchProjections(
      final List<ClusterVariableEntity> clusterVariableEntities, final boolean truncateValues) {
    return clusterVariableEntities.stream()
        .map(clusterVariableEntity -> toSearchProjection(clusterVariableEntity, truncateValues))
        .toList();
  }

  public static ClusterVariableSearchResult toSearchProjection(
      final ClusterVariableEntity clusterVariableEntity, final boolean truncateValues) {
    final var strictBase = toStrictBase(clusterVariableEntity);
    final var strictSearch = toStrictSearch(clusterVariableEntity, truncateValues);
    final var clusterVariableResult =
        new ClusterVariableSearchResult()
            .name(strictBase.name())
            .value(strictSearch.value())
            .isTruncated(strictSearch.isTruncated());
    return applyScope(clusterVariableResult, strictBase);
  }

  public static ClusterVariableResult toItemProjection(
      final ClusterVariableEntity clusterVariableEntity) {
    final var strictBase = toStrictBase(clusterVariableEntity);
    final var strictItem = toStrictItem(clusterVariableEntity);
    final var clusterVariableResult =
        new ClusterVariableResult().name(strictBase.name()).value(strictItem.value());
    return applyScope(clusterVariableResult, strictBase);
  }

  private static GeneratedClusterVariableResultBaseStrictContract toStrictBase(
      final ClusterVariableEntity clusterVariableEntity) {
    return GeneratedClusterVariableResultBaseStrictContract.builder()
        .name(clusterVariableEntity.name(), ContractPolicy.requiredNonNull())
        .scope(
            ContractPolicy.mapEnum(
                clusterVariableEntity.scope(), ClusterVariableScopeEnum::fromValue),
            ContractPolicy.requiredNonNull())
        .tenantId(clusterVariableEntity.tenantId())
        .build();
  }

  private static GeneratedClusterVariableSearchStrictContract toStrictSearch(
      final ClusterVariableEntity clusterVariableEntity, final boolean truncateValues) {
    return GeneratedClusterVariableSearchStrictContract.builder()
        .value(
            !truncateValues
                ? ContractPolicy.resolvePreviewValue(
                    clusterVariableEntity.value(),
                    clusterVariableEntity.fullValue(),
                    clusterVariableEntity.isPreview())
                : clusterVariableEntity.value(),
            ContractPolicy.requiredNonNull())
        .isTruncated(
            truncateValues && clusterVariableEntity.isPreview(), ContractPolicy.requiredNonNull())
        .build();
  }

  private static GeneratedClusterVariableStrictContract toStrictItem(
      final ClusterVariableEntity clusterVariableEntity) {
    return GeneratedClusterVariableStrictContract.builder()
        .value(
            ContractPolicy.resolvePreviewValue(
                clusterVariableEntity.value(),
                clusterVariableEntity.fullValue(),
                clusterVariableEntity.isPreview()),
            ContractPolicy.requiredNonNull())
        .build();
  }

  private static <T extends io.camunda.gateway.protocol.model.ClusterVariableResultBase>
      T applyScope(
          final T result, final GeneratedClusterVariableResultBaseStrictContract strictBase) {
    result.scope(strictBase.scope());
    if (strictBase.tenantId() != null) {
      result.tenantId(strictBase.tenantId());
    }
    return result;
  }
}
