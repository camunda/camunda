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
import io.camunda.gateway.protocol.model.ProcessInstance;
import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ProcessInstanceContractAdapter {

  private ProcessInstanceContractAdapter() {}

  public static List<ProcessInstance> adapt(final List<ProcessInstanceEntity> entities) {
    return entities.stream().map(ProcessInstanceContractAdapter::adapt).toList();
  }

  public static ProcessInstance adapt(final ProcessInstanceEntity entity) {
    return new ProcessInstance()
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), "processDefinitionId", entity))
        .processDefinitionVersion(
            ContractPolicy.requireNonNull(
                entity.processDefinitionVersion(), "processDefinitionVersion", entity))
        .startDate(
            ContractPolicy.requireNonNull(formatDate(entity.startDate()), "startDate", entity))
        .state(ContractPolicy.requireNonNull(toProtocolState(entity.state()), "state", entity))
        .hasIncident(ContractPolicy.requireNonNull(entity.hasIncident(), "hasIncident", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .tags(ContractPolicy.requireNonNull(entity.tags(), "tags", entity))
        .processDefinitionName(entity.processDefinitionName())
        .processDefinitionVersionTag(entity.processDefinitionVersionTag())
        .endDate(formatDate(entity.endDate()))
        .parentProcessInstanceKey(KeyUtil.keyToString(entity.parentProcessInstanceKey()))
        .parentElementInstanceKey(KeyUtil.keyToString(entity.parentFlowNodeInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(entity.rootProcessInstanceKey()))
        .businessId(emptyToNull(entity.businessId()));
  }

  /**
   * Maps the internal CANCELED state to the API's TERMINATED state. All other states map directly
   * by name.
   */
  private static @Nullable ProcessInstanceStateEnum toProtocolState(
      final ProcessInstanceState value) {
    if (value == null) {
      return null;
    }
    if (value == ProcessInstanceState.CANCELED) {
      return ProcessInstanceStateEnum.TERMINATED;
    }
    return ProcessInstanceStateEnum.fromValue(value.name());
  }

  private static @Nullable String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
