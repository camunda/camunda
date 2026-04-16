/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;

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
            requireNonNull(entity.decisionDefinitionId(), "decisionDefinitionId", entity))
        .decisionDefinitionKey(
            requireNonNull(
                keyToString(entity.decisionDefinitionKey()), "decisionDefinitionKey", entity))
        .decisionRequirementsId(
            requireNonNull(entity.decisionRequirementsId(), "decisionRequirementsId", entity))
        .decisionRequirementsKey(
            requireNonNull(
                keyToString(entity.decisionRequirementsKey()), "decisionRequirementsKey", entity))
        .decisionRequirementsName(
            requireNonNull(entity.decisionRequirementsName(), "decisionRequirementsName", entity))
        .decisionRequirementsVersion(
            requireNonNull(
                entity.decisionRequirementsVersion(), "decisionRequirementsVersion", entity))
        .name(requireNonNull(entity.name(), "name", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .version(requireNonNull(entity.version(), "version", entity));
  }
}
