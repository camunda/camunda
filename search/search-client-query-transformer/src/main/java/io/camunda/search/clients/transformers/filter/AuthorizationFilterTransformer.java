/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex;
import java.util.List;

public final class AuthorizationFilterTransformer
    implements FilterTransformer<AuthorizationFilter> {

  private final String prefix;

  public AuthorizationFilterTransformer(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public SearchQuery toSearchQuery(final AuthorizationFilter filter) {
    return and(
        longTerms("ownerKey", filter.ownerKeys()),
        filter.ownerType() == null ? null : term("ownerType", filter.ownerType()),
        stringTerms("permissions.resourceIds", filter.resourceKeys()),
        filter.resourceType() == null ? null : term("resourceType", filter.resourceType()),
        filter.permissionType() == null
            ? null
            : term("permissions.type", filter.permissionType().name()));
  }

  @Override
  public List<String> toIndices(final AuthorizationFilter filter) {
    String indexName = AuthorizationIndex.getIndexNameWithPrefix(prefix);
    return List.of(indexName);
  }
}
