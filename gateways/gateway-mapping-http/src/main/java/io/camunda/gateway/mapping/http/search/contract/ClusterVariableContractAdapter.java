/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableScopeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.ClusterVariableEntity;
import java.util.List;

/**
 * Contract adaptation layer for cluster variable projections.
 *
 * <p>Policy in this adapter controls preview/full value emission and the response shape differences
 * between search and item projections. The generated DTOs flatten the OpenAPI allOf inheritance so
 * each projection is a self-contained record that Jackson can serialize directly.
 */
public final class ClusterVariableContractAdapter {

  private ClusterVariableContractAdapter() {}

  public static List<GeneratedClusterVariableSearchStrictContract> toSearchProjections(
      final List<ClusterVariableEntity> clusterVariableEntities, final boolean truncateValues) {
    return clusterVariableEntities.stream()
        .map(entity -> toSearchProjection(entity, truncateValues))
        .toList();
  }

  public static GeneratedClusterVariableSearchStrictContract toSearchProjection(
      final ClusterVariableEntity entity, final boolean truncateValues) {
    return GeneratedClusterVariableSearchStrictContract.builder()
        .name(
            ContractPolicy.requireNonNull(
                entity.name(), GeneratedClusterVariableSearchStrictContract.Fields.NAME, entity))
        .scope(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.scope(), GeneratedClusterVariableScopeEnum::fromValue),
                GeneratedClusterVariableSearchStrictContract.Fields.SCOPE,
                entity))
        .value(
            ContractPolicy.requireNonNull(
                !truncateValues
                    ? ContractPolicy.resolvePreviewValue(
                        entity.value(), entity.fullValue(), entity.isPreview())
                    : entity.value(),
                GeneratedClusterVariableSearchStrictContract.Fields.VALUE,
                entity))
        .isTruncated(
            ContractPolicy.requireNonNull(
                truncateValues && entity.isPreview(),
                GeneratedClusterVariableSearchStrictContract.Fields.IS_TRUNCATED,
                entity))
        .tenantId(entity.tenantId())
        .build();
  }

  public static GeneratedClusterVariableStrictContract toItemProjection(
      final ClusterVariableEntity entity) {
    return GeneratedClusterVariableStrictContract.builder()
        .name(
            ContractPolicy.requireNonNull(
                entity.name(), GeneratedClusterVariableStrictContract.Fields.NAME, entity))
        .scope(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.scope(), GeneratedClusterVariableScopeEnum::fromValue),
                GeneratedClusterVariableStrictContract.Fields.SCOPE,
                entity))
        .value(
            ContractPolicy.requireNonNull(
                ContractPolicy.resolvePreviewValue(
                    entity.value(), entity.fullValue(), entity.isPreview()),
                GeneratedClusterVariableStrictContract.Fields.VALUE,
                entity))
        .tenantId(entity.tenantId())
        .build();
  }
}
