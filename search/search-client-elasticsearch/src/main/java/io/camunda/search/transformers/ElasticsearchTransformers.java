/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchConstantScoreQuery;
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchHasChildQuery;
import io.camunda.search.clients.query.SearchIdsQuery;
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchMatchQuery;
import io.camunda.search.clients.query.SearchPrefixQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.sort.SearchFieldSort;
import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.transformers.query.BoolQueryTransformer;
import io.camunda.search.transformers.query.ConstantScoreQueryTransformer;
import io.camunda.search.transformers.query.ExistsQueryTransformer;
import io.camunda.search.transformers.query.HasChildQueryTransformer;
import io.camunda.search.transformers.query.IdsQueryTransformer;
import io.camunda.search.transformers.query.MatchAllQueryTransformer;
import io.camunda.search.transformers.query.MatchNoneQueryTransformer;
import io.camunda.search.transformers.query.MatchQueryTransformer;
import io.camunda.search.transformers.query.PrefixQueryTransformer;
import io.camunda.search.transformers.query.QueryTransformer;
import io.camunda.search.transformers.query.RangeQueryTransformer;
import io.camunda.search.transformers.query.TermQueryTransformer;
import io.camunda.search.transformers.query.TermsQueryTransformer;
import io.camunda.search.transformers.search.SearchRequestTransformer;
import io.camunda.search.transformers.sort.FieldSortTransformer;
import io.camunda.search.transformers.sort.SortOptionsTransformer;
import io.camunda.search.transformers.types.TypedValueTransformer;
import java.util.HashMap;
import java.util.Map;

public final class ElasticsearchTransformers {

  private final Map<Class<?>, SearchTransfomer<?, ?>> transformers;

  public ElasticsearchTransformers() {
    transformers = new HashMap<>();
    initializeTransformers(this);
  }

  public <T, R> SearchTransfomer<T, R> getTransformer(final Class<?> cls) {
    return (SearchTransfomer<T, R>) transformers.get(cls);
  }

  private void put(final Class<?> cls, final SearchTransfomer<?, ?> mapper) {
    transformers.put(cls, mapper);
  }

  public static void initializeTransformers(final ElasticsearchTransformers mappers) {
    // requests/response
    mappers.put(SearchQueryRequest.class, new SearchRequestTransformer(mappers));
    mappers.put(SearchQueryResponse.class, new SearchRequestTransformer(mappers));
    mappers.put(SearchQueryHit.class, new SearchRequestTransformer(mappers));

    // queries
    mappers.put(SearchQuery.class, new QueryTransformer(mappers));
    mappers.put(SearchBoolQuery.class, new BoolQueryTransformer(mappers));
    mappers.put(SearchConstantScoreQuery.class, new ConstantScoreQueryTransformer(mappers));
    mappers.put(SearchExistsQuery.class, new ExistsQueryTransformer(mappers));
    mappers.put(SearchHasChildQuery.class, new HasChildQueryTransformer(mappers));
    mappers.put(SearchIdsQuery.class, new IdsQueryTransformer(mappers));
    mappers.put(SearchMatchAllQuery.class, new MatchAllQueryTransformer(mappers));
    mappers.put(SearchMatchNoneQuery.class, new MatchNoneQueryTransformer(mappers));
    mappers.put(SearchMatchQuery.class, new MatchQueryTransformer(mappers));
    mappers.put(SearchPrefixQuery.class, new PrefixQueryTransformer(mappers));
    mappers.put(SearchRangeQuery.class, new RangeQueryTransformer(mappers));
    mappers.put(SearchTermQuery.class, new TermQueryTransformer(mappers));
    mappers.put(SearchTermsQuery.class, new TermsQueryTransformer(mappers));

    // sort
    mappers.put(SearchSortOptions.class, new SortOptionsTransformer(mappers));
    mappers.put(SearchFieldSort.class, new FieldSortTransformer(mappers));

    // types
    mappers.put(TypedValue.class, new TypedValueTransformer(mappers));
  }
}
