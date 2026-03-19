/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceStateEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import java.util.List;

public final class ElementInstanceContractAdapter {

  private ElementInstanceContractAdapter() {}

  public static List<GeneratedElementInstanceStrictContract> adapt(
      final List<FlowNodeInstanceEntity> entities) {
    return entities.stream().map(ElementInstanceContractAdapter::adapt).toList();
  }

  public static GeneratedElementInstanceStrictContract adapt(final FlowNodeInstanceEntity entity) {
    return GeneratedElementInstanceStrictContract.builder()
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .startDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.startDate()), Fields.START_DATE, entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), Fields.ELEMENT_ID, entity))
        .elementName(
            ContractPolicy.requireNonNull(entity.flowNodeName(), Fields.ELEMENT_NAME, entity))
        .type(
            ContractPolicy.requireNonNull(
                entity.type() != null ? entity.type().name() : null, Fields.TYPE, entity))
        .state(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.state(), GeneratedElementInstanceStateEnum::fromValue),
                Fields.STATE,
                entity))
        .hasIncident(
            ContractPolicy.requireNonNull(entity.hasIncident(), Fields.HAS_INCIDENT, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                entity.flowNodeInstanceKey(), Fields.ELEMENT_INSTANCE_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .endDate(formatDate(entity.endDate()))
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .incidentKey(entity.incidentKey())
        .build();
  }
}
