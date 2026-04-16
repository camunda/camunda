/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.Authorization;
import io.camunda.gateway.protocol.model.OwnerTypeEnum;
import io.camunda.gateway.protocol.model.PermissionTypeEnum;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.search.entities.AuthorizationEntity;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AuthorizationContractAdapter {

  private AuthorizationContractAdapter() {}

  public static List<Authorization> adapt(final List<AuthorizationEntity> entities) {
    return entities.stream().map(AuthorizationContractAdapter::adapt).toList();
  }

  public static Authorization adapt(final AuthorizationEntity entity) {
    return new Authorization()
        .ownerId(ContractPolicy.requireNonNull(entity.ownerId(), "ownerId", entity))
        .ownerType(
            ContractPolicy.requireNonNull(
                OwnerTypeEnum.fromValue(entity.ownerType()), "ownerType", entity))
        .resourceType(
            ContractPolicy.requireNonNull(
                ResourceTypeEnum.valueOf(entity.resourceType()), "resourceType", entity))
        .permissionTypes(
            ContractPolicy.requireNonNull(
                entity.permissionTypes().stream()
                    .map(pt -> pt.name())
                    .map(PermissionTypeEnum::fromValue)
                    .toList(),
                "permissionTypes",
                entity))
        .authorizationKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.authorizationKey()), "authorizationKey", entity))
        .resourceId(emptyToNull(entity.resourceId()))
        .resourcePropertyName(emptyToNull(entity.resourcePropertyName()));
  }

  private static @Nullable String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
