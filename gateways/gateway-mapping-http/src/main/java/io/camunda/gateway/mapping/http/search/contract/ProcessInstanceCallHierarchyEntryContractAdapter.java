/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCallHierarchyEntryStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCallHierarchyEntryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.ProcessInstanceEntity;

/**
 * Contract adaptation layer for ProcessInstanceCallHierarchyEntry.
 *
 * <p>POC demonstration #3 for reviewers: capture business fallback policy in an adapter while
 * generated strict contracts handle deterministic key coercion and nullability checks.
 *
 * <p>In this slice, policy falls back to processDefinitionId when processDefinitionName is blank.
 */
public final class ProcessInstanceCallHierarchyEntryContractAdapter {

  private ProcessInstanceCallHierarchyEntryContractAdapter() {}

  public static GeneratedProcessInstanceCallHierarchyEntryStrictContract adapt(
      final ProcessInstanceEntity entity) {
    final var processDefinitionNameOrId =
        ContractPolicy.defaultIfNull(
            ContractPolicy.blankToNull(entity.processDefinitionName()),
            entity.processDefinitionId());

    return GeneratedProcessInstanceCallHierarchyEntryStrictContract.builder()
        .processInstanceKey(
            ContractPolicy.requireNonNull(
                entity.processInstanceKey(), Fields.PROCESS_INSTANCE_KEY, entity))
        .processDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.processDefinitionKey(), Fields.PROCESS_DEFINITION_KEY, entity))
        .processDefinitionName(
            ContractPolicy.requireNonNull(
                processDefinitionNameOrId, Fields.PROCESS_DEFINITION_NAME, entity))
        .build();
  }
}
