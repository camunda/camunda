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
import io.camunda.gateway.protocol.model.Incident;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.search.entities.IncidentEntity;
import java.util.List;

public final class IncidentContractAdapter {

  private IncidentContractAdapter() {}

  public static List<Incident> adapt(final List<IncidentEntity> entities) {
    return entities.stream().map(IncidentContractAdapter::adapt).toList();
  }

  public static Incident adapt(final IncidentEntity entity) {
    return new Incident()
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .errorType(
            ContractPolicy.requireNonNull(
                ContractPolicy.mapEnum(entity.errorType(), IncidentErrorTypeEnum::fromValue),
                "errorType",
                entity))
        .errorMessage(ContractPolicy.requireNonNull(entity.errorMessage(), "errorMessage", entity))
        .elementId(ContractPolicy.requireNonNull(entity.flowNodeId(), "elementId", entity))
        .creationTime(
            ContractPolicy.requireNonNull(
                formatDate(entity.creationTime()), "creationTime", entity))
        .state(
            ContractPolicy.requireNonNull(
                entity.state() != null
                    ? IncidentStateEnum.fromValue(entity.state().name())
                    : IncidentStateEnum.UNKNOWN,
                "state",
                entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .incidentKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.incidentKey()), "incidentKey", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .elementInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.flowNodeInstanceKey()), "elementInstanceKey", entity))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .jobKey(KeyUtil.keyToString(entity.jobKey()));
  }
}
