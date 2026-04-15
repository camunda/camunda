/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GlobalTaskListenerContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GlobalListenerSourceEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalTaskListenerContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.GlobalListenerEntity;
import java.util.List;

public final class GlobalTaskListenerContractAdapter {

  private GlobalTaskListenerContractAdapter() {}

  public static List<GlobalTaskListenerContract> adapt(final List<GlobalListenerEntity> entities) {
    return entities.stream().map(GlobalTaskListenerContractAdapter::adapt).toList();
  }

  public static GlobalTaskListenerContract adapt(final GlobalListenerEntity entity) {
    return GlobalTaskListenerContract.builder()
        .type(ContractPolicy.requireNonNull(entity.type(), Fields.TYPE, entity))
        .retries(ContractPolicy.requireNonNull(entity.retries(), Fields.RETRIES, entity))
        .afterNonGlobal(
            ContractPolicy.requireNonNull(entity.afterNonGlobal(), Fields.AFTER_NON_GLOBAL, entity))
        .priority(ContractPolicy.requireNonNull(entity.priority(), Fields.PRIORITY, entity))
        .eventTypes(
            ContractPolicy.requireNonNull(
                entity.eventTypes().stream()
                    .map(GlobalTaskListenerEventTypeEnum::fromValue)
                    .toList(),
                Fields.EVENT_TYPES,
                entity))
        .id(ContractPolicy.requireNonNull(entity.listenerId(), Fields.ID, entity))
        .source(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.source(), GlobalListenerSourceEnum::fromValue),
                Fields.SOURCE,
                entity))
        .build();
  }
}
