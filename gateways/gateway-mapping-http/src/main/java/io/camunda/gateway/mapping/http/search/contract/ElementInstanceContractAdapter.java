/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.ElementInstance;
import io.camunda.gateway.protocol.model.ElementInstanceStateEnum;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import java.util.List;

public final class ElementInstanceContractAdapter {

  private ElementInstanceContractAdapter() {}

  public static List<ElementInstance> adapt(final List<FlowNodeInstanceEntity> entities) {
    return entities.stream().map(ElementInstanceContractAdapter::adapt).toList();
  }

  public static ElementInstance adapt(final FlowNodeInstanceEntity entity) {
    return new ElementInstance()
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .startDate(
            ContractPolicy.requireNonNull(formatDate(entity.startDate()), "startDate", entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), "elementId", entity))
        .elementName(ContractPolicy.requireNonNull(entity.flowNodeName(), "elementName", entity))
        .type(
            ContractPolicy.requireNonNull(
                entity.type() != null ? entity.type().name() : null, "type", entity))
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.state(), ElementInstanceStateEnum::fromValue),
                "state",
                entity))
        .hasIncident(ContractPolicy.requireNonNull(entity.hasIncident(), "hasIncident", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.flowNodeInstanceKey()), "elementInstanceKey", entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .endDate(formatDate(entity.endDate()))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .incidentKey(KeyUtil.keyToString(entity.incidentKey()));
  }
}
