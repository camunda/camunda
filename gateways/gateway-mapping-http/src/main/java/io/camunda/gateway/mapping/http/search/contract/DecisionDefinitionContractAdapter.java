/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.DecisionDefinitionResult;
import io.camunda.search.entities.DecisionDefinitionEntity;
import java.util.List;

public final class DecisionDefinitionContractAdapter {

  private DecisionDefinitionContractAdapter() {}

  public static List<DecisionDefinitionResult> adapt(
      final List<DecisionDefinitionEntity> entities) {
    return entities.stream().map(DecisionDefinitionContractAdapter::adapt).toList();
  }

  public static DecisionDefinitionResult adapt(final DecisionDefinitionEntity entity) {
    return new DecisionDefinitionResult()
        .decisionDefinitionId(
            ContractPolicy.requireNonNull(
                entity.decisionDefinitionId(), "decisionDefinitionId", entity))
        .decisionDefinitionKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.decisionDefinitionKey()),
                "decisionDefinitionKey",
                entity))
        .decisionRequirementsId(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsId(), "decisionRequirementsId", entity))
        .decisionRequirementsKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.decisionRequirementsKey()),
                "decisionRequirementsKey",
                entity))
        .decisionRequirementsName(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsName(), "decisionRequirementsName", entity))
        .decisionRequirementsVersion(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsVersion(), "decisionRequirementsVersion", entity))
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .version(ContractPolicy.requireNonNull(entity.version(), "version", entity));
  }
}
