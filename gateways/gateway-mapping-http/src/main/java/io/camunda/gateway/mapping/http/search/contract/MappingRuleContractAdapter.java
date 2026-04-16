/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy.requireNonNull;

import io.camunda.gateway.protocol.model.MappingRuleResult;
import io.camunda.search.entities.MappingRuleEntity;
import java.util.List;

public final class MappingRuleContractAdapter {

  private MappingRuleContractAdapter() {}

  public static List<MappingRuleResult> adapt(final List<MappingRuleEntity> entities) {
    return entities.stream().map(MappingRuleContractAdapter::adapt).toList();
  }

  public static MappingRuleResult adapt(final MappingRuleEntity entity) {
    return new MappingRuleResult()
        .claimName(requireNonNull(entity.claimName(), "claimName", entity))
        .claimValue(requireNonNull(entity.claimValue(), "claimValue", entity))
        .name(requireNonNull(entity.name(), "name", entity))
        .mappingRuleId(requireNonNull(entity.mappingRuleId(), "mappingRuleId", entity));
  }
}
