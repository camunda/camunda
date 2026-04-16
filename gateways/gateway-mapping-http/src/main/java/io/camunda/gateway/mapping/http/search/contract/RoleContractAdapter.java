/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.Role;
import io.camunda.search.entities.RoleEntity;
import java.util.List;

public final class RoleContractAdapter {

  private RoleContractAdapter() {}

  public static List<Role> adapt(final List<RoleEntity> entities) {
    return entities.stream().map(RoleContractAdapter::adapt).toList();
  }

  public static Role adapt(final RoleEntity entity) {
    return new Role()
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .roleId(ContractPolicy.requireNonNull(entity.roleId(), "roleId", entity))
        .description(entity.description());
  }
}
