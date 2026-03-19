/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentErrorTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStateEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.IncidentEntity;
import java.util.List;

public final class IncidentContractAdapter {

  private IncidentContractAdapter() {}

  public static List<GeneratedIncidentStrictContract> adapt(final List<IncidentEntity> entities) {
    return entities.stream().map(IncidentContractAdapter::adapt).toList();
  }

  public static GeneratedIncidentStrictContract adapt(final IncidentEntity entity) {
    return GeneratedIncidentStrictContract.builder()
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .errorType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(
                    entity.errorType(), GeneratedIncidentErrorTypeEnum::fromValue),
                Fields.ERROR_TYPE,
                entity))
        .errorMessage(
            ContractPolicy.requireNonNull(entity.errorMessage(), Fields.ERROR_MESSAGE, entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), Fields.ELEMENT_ID, entity))
        .creationTime(
            ContractPolicy.requireNonNull(
                formatDate(entity.creationTime()), Fields.CREATION_TIME, entity))
        .state(
            ContractPolicy.requireNonNull(
                entity.state() != null
                    ? GeneratedIncidentStateEnum.fromValue(entity.state().name())
                    : GeneratedIncidentStateEnum.UNKNOWN,
                Fields.STATE,
                entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .incidentKey(
            ContractPolicy.requireNonNull(entity.incidentKey(), Fields.INCIDENT_KEY, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                entity.flowNodeInstanceKey(), Fields.ELEMENT_INSTANCE_KEY, entity))
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .jobKey(entity.jobKey())
        .build();
  }
}
