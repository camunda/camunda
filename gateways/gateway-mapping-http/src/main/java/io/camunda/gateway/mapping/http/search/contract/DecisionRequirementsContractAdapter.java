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
import io.camunda.gateway.protocol.model.DecisionRequirements;
import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.List;

public final class DecisionRequirementsContractAdapter {

  private DecisionRequirementsContractAdapter() {}

  public static List<DecisionRequirements> adapt(final List<DecisionRequirementsEntity> entities) {
    return entities.stream().map(DecisionRequirementsContractAdapter::adapt).toList();
  }

  public static DecisionRequirements adapt(final DecisionRequirementsEntity entity) {
    return new DecisionRequirements()
        .decisionRequirementsId(
            ContractPolicy.requireNonNull(
                entity.decisionRequirementsId(), "decisionRequirementsId", entity))
        .decisionRequirementsKey(
            ContractPolicy.requireNonNull(
                KeyUtil.keyToString(entity.decisionRequirementsKey()),
                "decisionRequirementsKey",
                entity))
        .decisionRequirementsName(
            ContractPolicy.requireNonNull(entity.name(), "decisionRequirementsName", entity))
        .resourceName(ContractPolicy.requireNonNull(entity.resourceName(), "resourceName", entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), "tenantId", entity))
        .version(ContractPolicy.requireNonNull(entity.version(), "version", entity));
  }
}
