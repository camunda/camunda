/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.TenantFilter;
import java.util.List;

public class TenantFilterTransformer implements FilterTransformer<TenantFilter> {

  @Override
  public SearchQuery toSearchQuery(final TenantFilter filter) {

    return and(
        filter.tenantKey() == null ? null : term("tenantKey", filter.tenantKey()),
        filter.tenantId() == null ? null : term("tenantId", filter.tenantId()),
        filter.name() == null ? null : term("name", filter.name()));
  }

  @Override
  public List<String> toIndices(final TenantFilter filter) {
    return List.of("camunda-tenant-8.7.0_alias");
  }
}
