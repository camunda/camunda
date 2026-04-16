/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

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
 * between search and item projections. The generated DTOs flatten the OpenAPI allOf inheritance so
 * each projection is a self-contained record that Jackson can serialize directly.
 */
public final class ClusterVariableContractAdapter {

  private ClusterVariableContractAdapter() {}

  public static List<ClusterVariableSearchResult> toSearchProjections(
      final List<ClusterVariableEntity> clusterVariableEntities, final boolean truncateValues) {
    return clusterVariableEntities.stream()
        .map(entity -> toSearchProjection(entity, truncateValues))
        .toList();
  }

  public static ClusterVariableSearchResult toSearchProjection(
      final ClusterVariableEntity entity, final boolean truncateValues) {
    return new ClusterVariableSearchResult()
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .scope(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.scope(), ClusterVariableScopeEnum::fromValue),
                "scope",
                entity))
        .value(
            ContractPolicy.requireNonNull(
                !truncateValues
                    ? ContractPolicy.resolvePreviewValue(
                        entity.value(), entity.fullValue(), entity.isPreview())
                    : entity.value(),
                "value",
                entity))
        .isTruncated(
            ContractPolicy.requireNonNull(
                truncateValues && entity.isPreview(), "isTruncated", entity))
        .tenantId(entity.tenantId());
  }

  public static ClusterVariableResult toItemProjection(final ClusterVariableEntity entity) {
    return new ClusterVariableResult()
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .scope(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.scope(), ClusterVariableScopeEnum::fromValue),
                "scope",
                entity))
        .value(
            ContractPolicy.requireNonNull(
                ContractPolicy.resolvePreviewValue(
                    entity.value(), entity.fullValue(), entity.isPreview()),
                "value",
                entity))
        .tenantId(entity.tenantId());
  }
}
