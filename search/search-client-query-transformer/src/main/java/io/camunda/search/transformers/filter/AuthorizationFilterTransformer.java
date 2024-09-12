/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.AuthorizationFilter;
import java.util.List;

public class AuthorizationFilterTransformer implements FilterTransformer<AuthorizationFilter> {

  @Override
  public SearchQuery toSearchQuery(final AuthorizationFilter filter) {
    return and(
        filter.ownerKey() == null ? null : term("value.ownerKey", filter.ownerKey()),
        filter.ownerType() == null ? null : term("value.ownerType", filter.ownerType()),
        filter.resourceKey() == null ? null : term("value.resourceKey", filter.resourceKey()),
        filter.resourceType() == null ? null : term("value.resourceType", filter.resourceType()));
  }

  @Override
  public List<String> toIndices(final AuthorizationFilter filter) {
    return List.of("zeebe-record-authorization");
  }
}
