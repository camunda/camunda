/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.GlobalListenerSourceEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerResult;
import io.camunda.search.entities.GlobalListenerEntity;
import java.util.List;

public final class GlobalTaskListenerContractAdapter {

  private GlobalTaskListenerContractAdapter() {}

  public static List<GlobalTaskListenerResult> adapt(final List<GlobalListenerEntity> entities) {
    return entities.stream().map(GlobalTaskListenerContractAdapter::adapt).toList();
  }

  public static GlobalTaskListenerResult adapt(final GlobalListenerEntity entity) {
    return new GlobalTaskListenerResult()
        .type(ContractPolicy.requireNonNull(entity.type(), "type", entity))
        .retries(ContractPolicy.requireNonNull(entity.retries(), "retries", entity))
        .afterNonGlobal(
            ContractPolicy.requireNonNull(entity.afterNonGlobal(), "afterNonGlobal", entity))
        .priority(ContractPolicy.requireNonNull(entity.priority(), "priority", entity))
        .eventTypes(
            ContractPolicy.requireNonNull(
                entity.eventTypes().stream()
                    .map(GlobalTaskListenerEventTypeEnum::fromValue)
                    .toList(),
                "eventTypes",
                entity))
        .id(ContractPolicy.requireNonNull(entity.listenerId(), "id", entity))
        .source(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.source(), GlobalListenerSourceEnum::fromValue),
                "source",
                entity));
  }
}
