/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

import io.camunda.gateway.protocol.model.RoleResult;
import io.camunda.search.entities.RoleEntity;
import java.util.List;

public final class RoleContractAdapter {

  private RoleContractAdapter() {}

  public static List<RoleResult> adapt(final List<RoleEntity> entities) {
    return entities.stream().map(RoleContractAdapter::adapt).toList();
  }

  public static RoleResult adapt(final RoleEntity entity) {
    return new RoleResult()
        .name(requireNonNull(entity.name(), "name", entity))
        .roleId(requireNonNull(entity.roleId(), "roleId", entity))
        .description(entity.description());
  }
}
