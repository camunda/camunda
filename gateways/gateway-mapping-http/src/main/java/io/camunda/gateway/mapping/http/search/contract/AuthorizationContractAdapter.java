/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.AuthorizationResult;
import io.camunda.gateway.protocol.model.OwnerTypeEnum;
import io.camunda.gateway.protocol.model.PermissionTypeEnum;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.search.entities.AuthorizationEntity;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AuthorizationContractAdapter {

  private AuthorizationContractAdapter() {}

  public static List<AuthorizationResult> adapt(final List<AuthorizationEntity> entities) {
    return entities.stream().map(AuthorizationContractAdapter::adapt).toList();
  }

  public static AuthorizationResult adapt(final AuthorizationEntity entity) {
    return new AuthorizationResult()
        .ownerId(requireNonNull(entity.ownerId(), "ownerId", entity))
        .ownerType(requireNonNull(OwnerTypeEnum.fromValue(entity.ownerType()), "ownerType", entity))
        .resourceType(
            requireNonNull(ResourceTypeEnum.valueOf(entity.resourceType()), "resourceType", entity))
        .permissionTypes(
            requireNonNull(
                entity.permissionTypes().stream()
                    .map(pt -> pt.name())
                    .map(PermissionTypeEnum::fromValue)
                    .toList(),
                "permissionTypes",
                entity))
        .authorizationKey(
            requireNonNull(
                KeyUtil.keyToString(entity.authorizationKey()), "authorizationKey", entity))
        .resourceId(emptyToNull(entity.resourceId()))
        .resourcePropertyName(emptyToNull(entity.resourcePropertyName()));
  }

  private static @Nullable String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
