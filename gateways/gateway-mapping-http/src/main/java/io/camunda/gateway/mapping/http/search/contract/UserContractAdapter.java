/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.User;
import io.camunda.search.entities.UserEntity;
import java.util.List;

public final class UserContractAdapter {

  private UserContractAdapter() {}

  public static List<User> adapt(final List<UserEntity> entities) {
    return entities.stream().map(UserContractAdapter::adapt).toList();
  }

  public static User adapt(final UserEntity entity) {
    return new User()
        .username(ContractPolicy.requireNonNull(entity.username(), "username", entity))
        .name(entity.name())
        .email(entity.email());
  }
}
