/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;

public interface SearchRequestTransformer<F extends FilterBase, S extends SortOption>
    extends ServiceTransformer<TypedSearchQuery<F, S>, SearchQueryRequest> {

  @Override
  default SearchQueryRequest apply(final TypedSearchQuery<F, S> query) {
    return applyWithAuthentication(query, null);
  }

  default SearchQueryRequest applyWithAuthentication(
      final TypedSearchQuery<F, S> value, final SearchQuery authCheck) {
    return toSearchQueryRequest(value, authCheck);
  }

  SearchQueryRequest toSearchQueryRequest(
      final TypedSearchQuery<F, S> query, final SearchQuery authCheck);
}
