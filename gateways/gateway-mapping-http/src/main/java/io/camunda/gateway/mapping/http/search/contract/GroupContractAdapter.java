/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.GroupEntity;
import java.util.List;

public final class GroupContractAdapter {

  private GroupContractAdapter() {}

  public static List<GeneratedGroupStrictContract> adapt(final List<GroupEntity> entities) {
    return entities.stream().map(GroupContractAdapter::adapt).toList();
  }

  public static GeneratedGroupStrictContract adapt(final GroupEntity entity) {
    return GeneratedGroupStrictContract.builder()
        .name(ContractPolicy.requireNonNull(entity.name(), Fields.NAME, entity))
        .groupId(ContractPolicy.requireNonNull(entity.groupId(), Fields.GROUP_ID, entity))
        .description(entity.description())
        .build();
  }
}
