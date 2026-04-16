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

import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentResult;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.search.entities.IncidentEntity;
import java.util.List;

public final class IncidentContractAdapter {

  private IncidentContractAdapter() {}

  public static List<IncidentResult> adapt(final List<IncidentEntity> entities) {
    return entities.stream().map(IncidentContractAdapter::adapt).toList();
  }

  public static IncidentResult adapt(final IncidentEntity entity) {
    return new IncidentResult()
        .processDefinitionId(
            requireNonNull(entity.processDefinitionId(), "processDefinitionId", entity))
        .errorType(
            requireNonNull(
                mapEnum(entity.errorType(), IncidentErrorTypeEnum::fromValue), "errorType", entity))
        .errorMessage(requireNonNull(entity.errorMessage(), "errorMessage", entity))
        .elementId(requireNonNull(entity.flowNodeId(), "elementId", entity))
        .creationTime(requireNonNull(formatDate(entity.creationTime()), "creationTime", entity))
        .state(
            requireNonNull(
                entity.state() != null
                    ? IncidentStateEnum.fromValue(entity.state().name())
                    : IncidentStateEnum.UNKNOWN,
                "state",
                entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .incidentKey(requireNonNull(keyToString(entity.incidentKey()), "incidentKey", entity))
        .processDefinitionKey(
            requireNonNull(
                keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processInstanceKey(
            requireNonNull(keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .elementInstanceKey(
            requireNonNull(keyToString(entity.flowNodeInstanceKey()), "elementInstanceKey", entity))
        .rootProcessInstanceKey(keyToString(entity.rootProcessInstanceKey()))
        .jobKey(keyToString(entity.jobKey()));
  }
}
