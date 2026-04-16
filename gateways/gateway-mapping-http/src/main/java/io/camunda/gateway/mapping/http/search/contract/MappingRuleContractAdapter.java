/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.protocol.model.MappingRule;
import io.camunda.search.entities.MappingRuleEntity;
import java.util.List;

public final class MappingRuleContractAdapter {

  private MappingRuleContractAdapter() {}

  public static List<MappingRule> adapt(final List<MappingRuleEntity> entities) {
    return entities.stream().map(MappingRuleContractAdapter::adapt).toList();
  }

  public static MappingRule adapt(final MappingRuleEntity entity) {
    return new MappingRule()
        .claimName(ContractPolicy.requireNonNull(entity.claimName(), "claimName", entity))
        .claimValue(ContractPolicy.requireNonNull(entity.claimValue(), "claimValue", entity))
        .name(ContractPolicy.requireNonNull(entity.name(), "name", entity))
        .mappingRuleId(
            ContractPolicy.requireNonNull(entity.mappingRuleId(), "mappingRuleId", entity));
  }
}
