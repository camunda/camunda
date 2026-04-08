/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedPermissionTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.AuthorizationEntity;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AuthorizationContractAdapter {

  private AuthorizationContractAdapter() {}

  public static List<GeneratedAuthorizationStrictContract> adapt(
      final List<AuthorizationEntity> entities) {
    return entities.stream().map(AuthorizationContractAdapter::adapt).toList();
  }

  public static GeneratedAuthorizationStrictContract adapt(final AuthorizationEntity entity) {
    return GeneratedAuthorizationStrictContract.builder()
        .ownerId(ContractPolicy.requireNonNull(entity.ownerId(), Fields.OWNER_ID, entity))
        .ownerType(
            ContractPolicy.requireNonNull(
                GeneratedOwnerTypeEnum.fromValue(entity.ownerType()), Fields.OWNER_TYPE, entity))
        .resourceType(
            ContractPolicy.requireNonNull(
                GeneratedResourceTypeEnum.valueOf(entity.resourceType()),
                Fields.RESOURCE_TYPE,
                entity))
        .permissionTypes(
            ContractPolicy.requireNonNull(
                entity.permissionTypes().stream()
                    .map(pt -> pt.name())
                    .map(GeneratedPermissionTypeEnum::fromValue)
                    .toList(),
                Fields.PERMISSION_TYPES,
                entity))
        .authorizationKey(
            ContractPolicy.requireNonNull(
                entity.authorizationKey(), Fields.AUTHORIZATION_KEY, entity))
        .resourceId(emptyToNull(entity.resourceId()))
        .resourcePropertyName(emptyToNull(entity.resourcePropertyName()))
        .build();
  }

  private static @Nullable String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
