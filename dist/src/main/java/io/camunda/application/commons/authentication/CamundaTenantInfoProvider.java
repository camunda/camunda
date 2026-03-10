/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.TenantInfo;
import io.camunda.auth.domain.spi.TenantInfoProvider;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.service.TenantServices;
import java.util.List;
import java.util.Objects;

/** Resolves tenant details (id + name) from tenant IDs by querying the tenant search index. */
public class CamundaTenantInfoProvider implements TenantInfoProvider {

  private final TenantServices tenantServices;

  public CamundaTenantInfoProvider(final TenantServices tenantServices) {
    this.tenantServices = tenantServices;
  }

  @Override
  public List<TenantInfo> getTenants(final List<String> tenantIds) {
    if (tenantIds == null || tenantIds.isEmpty()) {
      return List.of();
    }
    final var query =
        SearchQueryBuilders.tenantSearchQuery(
            fn -> fn.filter(f -> f.tenantIds(tenantIds)).page(p -> p.size(tenantIds.size())));
    return tenantServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .search(query)
        .items()
        .stream()
        .filter(Objects::nonNull)
        .map(entity -> new TenantInfo(entity.tenantId(), entity.name()))
        .toList();
  }
}
