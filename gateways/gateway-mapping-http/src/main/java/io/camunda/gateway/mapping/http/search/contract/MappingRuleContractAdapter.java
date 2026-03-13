/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleStrictContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleStrictContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.MappingRuleEntity;
import java.util.List;

public final class MappingRuleContractAdapter {

  private MappingRuleContractAdapter() {}

  public static List<GeneratedMappingRuleStrictContract> adapt(
      final List<MappingRuleEntity> entities) {
    return entities.stream().map(MappingRuleContractAdapter::adapt).toList();
  }

  public static GeneratedMappingRuleStrictContract adapt(final MappingRuleEntity entity) {
    return GeneratedMappingRuleStrictContract.builder()
        .claimName(ContractPolicy.requireNonNull(entity.claimName(), Fields.CLAIM_NAME, entity))
        .claimValue(ContractPolicy.requireNonNull(entity.claimValue(), Fields.CLAIM_VALUE, entity))
        .name(ContractPolicy.requireNonNull(entity.name(), Fields.NAME, entity))
        .mappingRuleId(
            ContractPolicy.requireNonNull(entity.mappingRuleId(), Fields.MAPPING_RULE_ID, entity))
        .build();
  }
}
