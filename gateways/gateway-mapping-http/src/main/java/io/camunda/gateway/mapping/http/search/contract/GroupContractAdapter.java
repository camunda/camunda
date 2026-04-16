/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.GroupResult;
import io.camunda.search.entities.GroupEntity;
import java.util.List;

public final class GroupContractAdapter {

  private GroupContractAdapter() {}

  public static List<GroupResult> adapt(final List<GroupEntity> entities) {
    return entities.stream().map(GroupContractAdapter::adapt).toList();
  }

  public static GroupResult adapt(final GroupEntity entity) {
    return new GroupResult()
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .groupId(ContractPolicy.requireNonNull(entity.groupId(), "groupId", entity))
        .description(entity.description());
  }
}
