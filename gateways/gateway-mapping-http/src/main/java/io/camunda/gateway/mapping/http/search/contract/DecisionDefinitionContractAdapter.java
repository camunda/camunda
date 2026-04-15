/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.DecisionDefinitionContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.DecisionDefinitionContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.DecisionDefinitionEntity;
import java.util.List;

public final class DecisionDefinitionContractAdapter {

  private DecisionDefinitionContractAdapter() {}

  public static List<DecisionDefinitionContract> adapt(
      final List<DecisionDefinitionEntity> entities) {
    return entities.stream().map(DecisionDefinitionContractAdapter::adapt).toList();
  }

  public static DecisionDefinitionContract adapt(final DecisionDefinitionEntity entity) {
    return DecisionDefinitionContract.builder()
        .decisionDefinitionId(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionId(), Fields.DECISION_DEFINITION_ID, entity))
        .decisionDefinitionKey(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionKey(), Fields.DECISION_DEFINITION_KEY, entity))
        .decisionRequirementsId(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsId(), Fields.DECISION_REQUIREMENTS_ID, entity))
        .decisionRequirementsKey(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsKey(), Fields.DECISION_REQUIREMENTS_KEY, entity))
        .decisionRequirementsName(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsName(), Fields.DECISION_REQUIREMENTS_NAME, entity))
        .decisionRequirementsVersion(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsVersion(), Fields.DECISION_REQUIREMENTS_VERSION, entity))
        .name(ContractPolicy.requireNonNull(entity.name(), Fields.NAME, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .version(ContractPolicy.requireNonNull(entity.version(), Fields.VERSION, entity))
        .build();
  }
}
