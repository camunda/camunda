/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.TypedSearchQuery;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.search.sort.SortOption;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.filter.AuthenticationTransformer;
import io.camunda.service.transformers.filter.DateValueFilterTransformer;
import io.camunda.service.transformers.filter.FilterTransformer;
import io.camunda.service.transformers.filter.ProcessInstanceFilterTransformer;
import io.camunda.service.transformers.filter.VariableValueFilterTransformer;
import io.camunda.service.transformers.query.SearchQueryResultTransformer;
import io.camunda.service.transformers.query.TypedSearchQueryTransformer;
import io.camunda.service.transformers.sort.FieldSortingTransformer;
import java.util.HashMap;
import java.util.Map;

public final class ServiceTransformers {

  private final Map<Class<?>, ServiceTransformer<?, ?>> transformers;

  public ServiceTransformers() {
    transformers = new HashMap<>();
    initializeTransformers(this);
  }

  public <F extends FilterBase, S extends SortOption>
      TypedSearchQueryTransformer<F, S> getTypedSearchQueryTransformer(final Class<?> cls) {
    final ServiceTransformer<TypedSearchQuery<F, S>, SearchQueryRequest> transformer =
        getTransformer(cls);
    return (TypedSearchQueryTransformer<F, S>) transformer;
  }

  public <F extends FilterBase> FilterTransformer<F> getFilterTransformer(final Class<?> cls) {
    final ServiceTransformer<F, SearchQuery> transformer = getTransformer(cls);
    return (FilterTransformer<F>) transformer;
  }

  public <T, R> ServiceTransformer<T, R> getTransformer(final Class<?> cls) {
    return (ServiceTransformer<T, R>) transformers.get(cls);
  }

  private void put(final Class<?> cls, final ServiceTransformer<?, ?> mapper) {
    transformers.put(cls, mapper);
  }

  public static void initializeTransformers(final ServiceTransformers mappers) {
    // query -> request
    mappers.put(
        ProcessInstanceQuery.class,
        new TypedSearchQueryTransformer<ProcessInstanceFilter, ProcessInstanceSort>(mappers));

    // search query response -> search query result
    mappers.put(SearchQueryResult.class, new SearchQueryResultTransformer());

    // sorting -> search sort options
    mappers.put(FieldSortingTransformer.class, new FieldSortingTransformer());

    // filters -> search query
    mappers.put(Authentication.class, new AuthenticationTransformer());
    mappers.put(ProcessInstanceFilter.class, new ProcessInstanceFilterTransformer(mappers));
    mappers.put(VariableValueFilter.class, new VariableValueFilterTransformer());
    mappers.put(DateValueFilter.class, new DateValueFilterTransformer());
  }
}
