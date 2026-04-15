/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.search.contract.generated.TenantContract.Fields;

import io.camunda.gateway.mapping.http.search.contract.generated.TenantContract;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.search.entities.TenantEntity;
import java.util.List;

public final class TenantContractAdapter {

  private TenantContractAdapter() {}

  public static List<TenantContract> adapt(final List<TenantEntity> entities) {
    return entities.stream().map(TenantContractAdapter::adapt).toList();
  }

  public static TenantContract adapt(final TenantEntity entity) {
    return TenantContract.builder()
        .name(ContractPolicy.requireNonNull(entity.name(), Fields.NAME, entity))
        .tenantId(ContractPolicy.requireNonNull(entity.tenantId(), Fields.TENANT_ID, entity))
        .description(entity.description())
        .build();
  }
}
