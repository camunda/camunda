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

import io.camunda.gateway.protocol.model.DecisionRequirementsResult;
import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.List;

public final class DecisionRequirementsContractAdapter {

  private DecisionRequirementsContractAdapter() {}

  public static List<DecisionRequirementsResult> adapt(
      final List<DecisionRequirementsEntity> entities) {
    return entities.stream().map(DecisionRequirementsContractAdapter::adapt).toList();
  }

  public static DecisionRequirementsResult adapt(final DecisionRequirementsEntity entity) {
    return new DecisionRequirementsResult()
        .decisionRequirementsId(
            requireNonNull(entity.decisionRequirementsId(), "decisionRequirementsId", entity))
        .decisionRequirementsKey(
            requireNonNull(
                keyToString(entity.decisionRequirementsKey()), "decisionRequirementsKey", entity))
        .decisionRequirementsName(requireNonNull(entity.name(), "decisionRequirementsName", entity))
        .resourceName(requireNonNull(entity.resourceName(), "resourceName", entity))
        .tenantId(requireNonNull(entity.tenantId(), "tenantId", entity))
        .version(requireNonNull(entity.version(), "version", entity));
  }
}
