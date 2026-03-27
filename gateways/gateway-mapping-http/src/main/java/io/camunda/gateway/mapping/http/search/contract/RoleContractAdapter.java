/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.RoleEntity;
import java.util.List;

public final class RoleContractAdapter {

  private RoleContractAdapter() {}

  public static List<GeneratedRoleStrictContract> adapt(final List<RoleEntity> entities) {
    return entities.stream().map(RoleContractAdapter::adapt).toList();
  }

  public static GeneratedRoleStrictContract adapt(final RoleEntity entity) {
    return GeneratedRoleStrictContract.builder()
        .name(ContractPolicy.requireNonNull(entity.name(), Fields.NAME, entity))
        .roleId(ContractPolicy.requireNonNull(entity.roleId(), Fields.ROLE_ID, entity))
        .description(entity.description())
        .build();
  }
}
