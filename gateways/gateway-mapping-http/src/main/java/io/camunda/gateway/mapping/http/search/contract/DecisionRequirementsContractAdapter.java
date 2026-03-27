/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.List;

public final class DecisionRequirementsContractAdapter {

  private DecisionRequirementsContractAdapter() {}

  public static List<GeneratedDecisionRequirementsStrictContract> adapt(
      final List<DecisionRequirementsEntity> entities) {
    return entities.stream().map(DecisionRequirementsContractAdapter::adapt).toList();
  }

  public static GeneratedDecisionRequirementsStrictContract adapt(
      final DecisionRequirementsEntity entity) {
    return GeneratedDecisionRequirementsStrictContract.builder()
        .decisionRequirementsId(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsId(), Fields.DECISION_REQUIREMENTS_ID, entity))
        .decisionRequirementsKey(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsKey(), Fields.DECISION_REQUIREMENTS_KEY, entity))
        .decisionRequirementsName(
            ContractPolicy.requireNonNull(entity.name(), Fields.DECISION_REQUIREMENTS_NAME, entity))
        .resourceName(
            ContractPolicy.requireNonNull(entity.resourceName(), Fields.RESOURCE_NAME, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .version(ContractPolicy.requireNonNull(entity.version(), Fields.VERSION, entity))
        .build();
  }
}
