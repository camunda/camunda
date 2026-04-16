/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.blankToNull;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.defaultIfNull;
import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.ProcessInstanceCallHierarchyEntry;
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

  public static ProcessInstanceCallHierarchyEntry adapt(final ProcessInstanceEntity entity) {
    final var processDefinitionNameOrId =
        defaultIfNull(blankToNull(entity.processDefinitionName()), entity.processDefinitionId());

    return new ProcessInstanceCallHierarchyEntry()
        .processInstanceKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processInstanceKey()), "processInstanceKey", entity))
        .processDefinitionKey(
            requireNonNull(
                KeyUtil.keyToString(entity.processDefinitionKey()), "processDefinitionKey", entity))
        .processDefinitionName(
            requireNonNull(processDefinitionNameOrId, "processDefinitionName", entity));
  }
}
