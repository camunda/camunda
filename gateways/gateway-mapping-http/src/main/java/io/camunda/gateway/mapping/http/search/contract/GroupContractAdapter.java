/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.Group;
import io.camunda.search.entities.GroupEntity;
import java.util.List;

public final class GroupContractAdapter {

  private GroupContractAdapter() {}

  public static List<Group> adapt(final List<GroupEntity> entities) {
    return entities.stream().map(GroupContractAdapter::adapt).toList();
  }

  public static Group adapt(final GroupEntity entity) {
    return new Group()
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .groupId(ContractPolicy.requireNonNull(entity.groupId(), "groupId", entity))
        .description(entity.description());
  }
}
