/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceStateEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ProcessInstanceContractAdapter {

  private ProcessInstanceContractAdapter() {}

  public static List<GeneratedProcessInstanceStrictContract> adapt(
      final List<ProcessInstanceEntity> entities) {
    return entities.stream().map(ProcessInstanceContractAdapter::adapt).toList();
  }

  public static GeneratedProcessInstanceStrictContract adapt(final ProcessInstanceEntity entity) {
    return GeneratedProcessInstanceStrictContract.builder()
        .processDefinitionId(
            ContractPolicy.requireNonNull(
                entity.processDefinitionId(), Fields.PROCESS_DEFINITION_ID, entity))
        .processDefinitionVersion(
            ContractPolicy.requireNonNull(
                entity.processDefinitionVersion(), Fields.PROCESS_DEFINITION_VERSION, entity))
        .startDate(
            ContractPolicy.requireNonNull(
                formatDate(entity.startDate()), Fields.START_DATE, entity))
        .state(ContractPolicy.requireNonNull(toProtocolState(entity.state()), Fields.STATE, entity))
        .hasIncident(
            ContractPolicy.requireNonNull(entity.hasIncident(), Fields.HAS_INCIDENT, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .tags(ContractPolicy.requireNonNull(entity.tags(), Fields.TAGS, entity))
        .processDefinitionName(entity.processDefinitionName())
        .processDefinitionVersionTag(entity.processDefinitionVersionTag())
        .endDate(formatDate(entity.endDate()))
        .parentProcessInstanceKey(entity.parentProcessInstanceKey())
        .parentElementInstanceKey(entity.parentFlowNodeInstanceKey())
        .rootProcessInstanceKey(entity.rootProcessInstanceKey())
        .businessId(emptyToNull(entity.businessId()))
        .build();
  }

  /**
   * Maps the internal CANCELED state to the API's TERMINATED state. All other states map directly
   * by name.
   */
  private static @Nullable GeneratedProcessInstanceStateEnum toProtocolState(
      final ProcessInstanceState value) {
    if (value == null) {
      return null;
    }
    if (value == ProcessInstanceState.CANCELED) {
      return GeneratedProcessInstanceStateEnum.TERMINATED;
    }
    return GeneratedProcessInstanceStateEnum.fromValue(value.name());
  }

  private static @Nullable String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
