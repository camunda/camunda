/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.mapEnum;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

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
        .type(requireNonNull(entity.type(), "type", entity))
        .retries(requireNonNull(entity.retries(), "retries", entity))
        .afterNonGlobal(requireNonNull(entity.afterNonGlobal(), "afterNonGlobal", entity))
        .priority(requireNonNull(entity.priority(), "priority", entity))
        .eventTypes(
            requireNonNull(
                entity.eventTypes().stream()
                    .map(GlobalTaskListenerEventTypeEnum::fromValue)
                    .toList(),
                "eventTypes",
                entity))
        .id(requireNonNull(entity.listenerId(), "id", entity))
        .source(
            requireNonNull(
                mapEnum(entity.source(), GlobalListenerSourceEnum::fromValue), "source", entity));
  }
}
