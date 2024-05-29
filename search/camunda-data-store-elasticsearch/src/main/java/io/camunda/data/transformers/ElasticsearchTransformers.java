/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.query.DataStoreBoolQuery;
import io.camunda.data.clients.query.DataStoreConstantScoreQuery;
import io.camunda.data.clients.query.DataStoreExistsQuery;
import io.camunda.data.clients.query.DataStoreHasChildQuery;
import io.camunda.data.clients.query.DataStoreIdsQuery;
import io.camunda.data.clients.query.DataStoreMatchAllQuery;
import io.camunda.data.clients.query.DataStoreMatchNoneQuery;
import io.camunda.data.clients.query.DataStoreMatchQuery;
import io.camunda.data.clients.query.DataStorePrefixQuery;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.query.DataStoreRangeQuery;
import io.camunda.data.clients.query.DataStoreTermQuery;
import io.camunda.data.clients.query.DataStoreTermsQuery;
import io.camunda.data.clients.sort.DataStoreFieldSort;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.types.DataStoreFieldValue;
import io.camunda.data.mappers.DataStoreTransformer;
import io.camunda.data.transformers.core.SearchRequestTransformer;
import io.camunda.data.transformers.query.BoolQueryTransformer;
import io.camunda.data.transformers.query.ConstantScoreQueryTransformer;
import io.camunda.data.transformers.query.ExistsQueryTransformer;
import io.camunda.data.transformers.query.HasChildQueryTransformer;
import io.camunda.data.transformers.query.IdsQueryTransformer;
import io.camunda.data.transformers.query.MatchAllQueryTransformer;
import io.camunda.data.transformers.query.MatchNoneQueryTransformer;
import io.camunda.data.transformers.query.MatchQueryTransformer;
import io.camunda.data.transformers.query.PrefixQueryTransformer;
import io.camunda.data.transformers.query.QueryTransformer;
import io.camunda.data.transformers.query.RangeQueryTransformer;
import io.camunda.data.transformers.query.TermQueryTransformer;
import io.camunda.data.transformers.query.TermsQueryTransformer;
import io.camunda.data.transformers.sort.FieldSortTransformer;
import io.camunda.data.transformers.sort.SortOptionsTransformer;
import io.camunda.data.transformers.types.FieldValueTransformer;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"rawtypes"})
public final class ElasticsearchTransformers {

  private final Map<Class, DataStoreTransformer<?, ?>> transformers;

  public ElasticsearchTransformers() {
    transformers = new HashMap<>();
    initializeTransformers(this);
  }

  public <T, R> DataStoreTransformer<T, R> getTransformer(final Class cls) {
    return (DataStoreTransformer<T, R>) transformers.get(cls);
  }

  private void put(final Class cls, final DataStoreTransformer mapper) {
    transformers.put(cls, mapper);
  }

  public static void initializeTransformers(final ElasticsearchTransformers mappers) {
    // requests
    mappers.put(DataStoreSearchRequest.class, new SearchRequestTransformer(mappers));

    // queries
    mappers.put(DataStoreQuery.class, new QueryTransformer(mappers));
    mappers.put(DataStoreBoolQuery.class, new BoolQueryTransformer(mappers));
    mappers.put(DataStoreConstantScoreQuery.class, new ConstantScoreQueryTransformer(mappers));
    mappers.put(DataStoreExistsQuery.class, new ExistsQueryTransformer(mappers));
    mappers.put(DataStoreHasChildQuery.class, new HasChildQueryTransformer(mappers));
    mappers.put(DataStoreIdsQuery.class, new IdsQueryTransformer(mappers));
    mappers.put(DataStoreMatchAllQuery.class, new MatchAllQueryTransformer(mappers));
    mappers.put(DataStoreMatchNoneQuery.class, new MatchNoneQueryTransformer(mappers));
    mappers.put(DataStoreMatchQuery.class, new MatchQueryTransformer(mappers));
    mappers.put(DataStorePrefixQuery.class, new PrefixQueryTransformer(mappers));
    mappers.put(DataStoreRangeQuery.class, new RangeQueryTransformer(mappers));
    mappers.put(DataStoreTermQuery.class, new TermQueryTransformer(mappers));
    mappers.put(DataStoreTermsQuery.class, new TermsQueryTransformer(mappers));

    // sort
    mappers.put(DataStoreSortOptions.class, new SortOptionsTransformer(mappers));
    mappers.put(DataStoreFieldSort.class, new FieldSortTransformer(mappers));

    // types
    mappers.put(DataStoreFieldValue.class, new FieldValueTransformer());
  }
}
