/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationContract;
import io.camunda.gateway.mapping.http.search.contract.generated.OwnerTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.PermissionTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.ResourceTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.AuthorizationEntity;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AuthorizationContractAdapter {

  private AuthorizationContractAdapter() {}

  public static List<AuthorizationContract> adapt(final List<AuthorizationEntity> entities) {
    return entities.stream().map(AuthorizationContractAdapter::adapt).toList();
  }

  public static AuthorizationContract adapt(final AuthorizationEntity entity) {
    return AuthorizationContract.builder()
        .ownerId(ContractPolicy.requireNonNull(entity.ownerId(), Fields.OWNER_ID, entity))
        .ownerType(
            ContractPolicy.requireNonNull(
                OwnerTypeEnum.fromValue(entity.ownerType()), Fields.OWNER_TYPE, entity))
        .resourceType(
            ContractPolicy.requireNonNull(
                ResourceTypeEnum.valueOf(entity.resourceType()), Fields.RESOURCE_TYPE, entity))
        .permissionTypes(
            ContractPolicy.requireNonNull(
                entity.permissionTypes().stream()
                    .map(pt -> pt.name())
                    .map(PermissionTypeEnum::fromValue)
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
