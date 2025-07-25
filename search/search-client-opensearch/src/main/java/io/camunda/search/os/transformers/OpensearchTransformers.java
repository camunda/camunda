/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers;

import io.camunda.search.clients.aggregator.SearchChildrenAggregator;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.clients.aggregator.SearchParentAggregator;
import io.camunda.search.clients.aggregator.SearchSumAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchConstantScoreQuery;
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchHasChildQuery;
import io.camunda.search.clients.query.SearchHasParentQuery;
import io.camunda.search.clients.query.SearchIdsQuery;
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchMatchPhraseQuery;
import io.camunda.search.clients.query.SearchMatchQuery;
import io.camunda.search.clients.query.SearchPrefixQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.query.SearchWildcardQuery;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.os.transformers.aggregator.SearchChildrenAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchCompositeAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchFilterAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchFiltersAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchParentAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchSumAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchTermsAggregatorTransformer;
import io.camunda.search.os.transformers.aggregator.SearchTopHitsAggregatorTransformer;
import io.camunda.search.os.transformers.query.BoolQueryTransformer;
import io.camunda.search.os.transformers.query.ConstantScoreQueryTransformer;
import io.camunda.search.os.transformers.query.ExistsQueryTransformer;
import io.camunda.search.os.transformers.query.HasChildQueryTransformer;
import io.camunda.search.os.transformers.query.HasParentQueryTransformer;
import io.camunda.search.os.transformers.query.IdsQueryTransformer;
import io.camunda.search.os.transformers.query.MatchAllQueryTransformer;
import io.camunda.search.os.transformers.query.MatchNoneQueryTransformer;
import io.camunda.search.os.transformers.query.MatchPhraseQueryTransformer;
import io.camunda.search.os.transformers.query.MatchQueryTransformer;
import io.camunda.search.os.transformers.query.PrefixQueryTransformer;
import io.camunda.search.os.transformers.query.QueryTransformer;
import io.camunda.search.os.transformers.query.RangeQueryTransformer;
import io.camunda.search.os.transformers.query.TermQueryTransformer;
import io.camunda.search.os.transformers.query.TermsQueryTransformer;
import io.camunda.search.os.transformers.query.WildcardQueryTransformer;
import io.camunda.search.os.transformers.search.SearchDeleteRequestTransformer;
import io.camunda.search.os.transformers.search.SearchGetRequestTransformer;
import io.camunda.search.os.transformers.search.SearchGetResponseTransformer;
import io.camunda.search.os.transformers.search.SearchIndexRequestTransformer;
import io.camunda.search.os.transformers.search.SearchQueryHitTransformer;
import io.camunda.search.os.transformers.search.SearchRequestTransformer;
import io.camunda.search.os.transformers.search.SearchResponseTransformer;
import io.camunda.search.os.transformers.search.SearchWriteResponseTransformer;
import io.camunda.search.os.transformers.sort.FieldSortTransformer;
import io.camunda.search.os.transformers.sort.SortOptionsTransformer;
import io.camunda.search.os.transformers.source.SourceConfigTransformer;
import io.camunda.search.os.transformers.source.SourceFilterTransformer;
import io.camunda.search.os.transformers.types.TypedValueTransformer;
import io.camunda.search.sort.SearchFieldSort;
import io.camunda.search.sort.SearchSortOptions;
import java.util.HashMap;
import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public final class OpensearchTransformers {

  private final Map<Class<?>, SearchTransfomer<?, ?>> transformers;

  public OpensearchTransformers() {
    transformers = new HashMap<>();
    initializeMappers(this);
  }

  public <T, R> SearchTransfomer<T, R> getTransformer(final Class<?> cls) {
    return (SearchTransfomer<T, R>) transformers.get(cls);
  }

  public <T, R extends Aggregation> SearchTransfomer<T, R> getSearchAggregationTransformer(
      final Class<?> cls) {
    return (SearchTransfomer<T, R>) transformers.get(cls);
  }

  private void put(final Class<?> cls, final SearchTransfomer<?, ?> mapper) {
    transformers.put(cls, mapper);
  }

  private static void initializeMappers(final OpensearchTransformers mappers) {
    // requests/response
    mappers.put(SearchQueryRequest.class, new SearchRequestTransformer(mappers));
    mappers.put(SearchQueryResponse.class, new SearchResponseTransformer(mappers));
    mappers.put(SearchQueryHit.class, new SearchQueryHitTransformer(mappers));

    // get request/response
    mappers.put(SearchGetRequest.class, new SearchGetRequestTransformer(mappers));
    mappers.put(SearchGetResponse.class, new SearchGetResponseTransformer(mappers));

    // write request/response
    mappers.put(SearchIndexRequest.class, new SearchIndexRequestTransformer(mappers));
    mappers.put(SearchDeleteRequest.class, new SearchDeleteRequestTransformer(mappers));
    mappers.put(SearchWriteResponse.class, new SearchWriteResponseTransformer(mappers));

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
    mappers.put(SearchWildcardQuery.class, new WildcardQueryTransformer(mappers));
    mappers.put(SearchHasParentQuery.class, new HasParentQueryTransformer(mappers));
    mappers.put(SearchMatchPhraseQuery.class, new MatchPhraseQueryTransformer(mappers));

    // aggregations
    mappers.put(SearchFilterAggregator.class, new SearchFilterAggregatorTransformer(mappers));
    mappers.put(SearchFiltersAggregator.class, new SearchFiltersAggregatorTransformer(mappers));
    mappers.put(SearchTermsAggregator.class, new SearchTermsAggregatorTransformer(mappers));
    mappers.put(SearchTopHitsAggregator.class, new SearchTopHitsAggregatorTransformer(mappers));
    mappers.put(SearchCompositeAggregator.class, new SearchCompositeAggregatorTransformer(mappers));
    mappers.put(SearchChildrenAggregator.class, new SearchChildrenAggregatorTransformer(mappers));
    mappers.put(SearchParentAggregator.class, new SearchParentAggregatorTransformer(mappers));
    mappers.put(SearchSumAggregator.class, new SearchSumAggregatorTransformer(mappers));

    // sort
    mappers.put(SearchSortOptions.class, new SortOptionsTransformer(mappers));
    mappers.put(SearchFieldSort.class, new FieldSortTransformer(mappers));

    // types
    mappers.put(TypedValue.class, new TypedValueTransformer(mappers));

    // source
    mappers.put(SearchSourceConfig.class, new SourceConfigTransformer(mappers));
    mappers.put(SearchSourceFilter.class, new SourceFilterTransformer(mappers));
  }
}
