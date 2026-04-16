/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.mapEnum;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;

import io.camunda.gateway.protocol.model.ElementInstanceResult;
import io.camunda.gateway.protocol.model.ElementInstanceStateEnum;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import java.util.List;

public final class ElementInstanceContractAdapter {

  private ElementInstanceContractAdapter() {}

  public static List<ElementInstanceResult> adapt(final List<FlowNodeInstanceEntity> entities) {
    return entities.stream().map(ElementInstanceContractAdapter::adapt).toList();
  }

  public static ElementInstanceResult adapt(final FlowNodeInstanceEntity entity) {
    return new ElementInstanceResult()
        .processDefinitionId(
            requireNonNull(entity.processDefinitionId(), "processDefinitionId", entity))
        .startDate(requireNonNull(formatDate(entity.startDate()), "startDate", entity))
        .elementId(requireNonNull(entity.flowNodeId(), "elementId", entity))
        .elementName(requireNonNull(entity.flowNodeName(), "elementName", entity))
        .type(requireNonNull(entity.type() != null ? entity.type().name() : null, "type", entity))
        .state(
            requireNonNull(
                mapEnum(entity.state(), ElementInstanceStateEnum::fromValue), "state", entity))
        .hasIncident(requireNonNull(entity.hasIncident(), "hasIncident", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .elementInstanceKey(
            requireNonNull(keyToString(entity.flowNodeInstanceKey()), "elementInstanceKey", entity))
        .processInstanceKey(
            requireNonNull(keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .processDefinitionKey(
            requireNonNull(
                keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .endDate(formatDate(entity.endDate()))
        .rootProcessInstanceKey(keyToString(entity.rootProcessInstanceKey()))
        .incidentKey(keyToString(entity.incidentKey()));
  }
}
