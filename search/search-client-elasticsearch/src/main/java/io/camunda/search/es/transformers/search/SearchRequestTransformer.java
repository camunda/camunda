/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.json.JsonData;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.sort.SearchSortOptions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SearchRequestTransformer
    extends ElasticsearchTransformer<SearchQueryRequest, SearchRequest> {

  public SearchRequestTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchRequest apply(final SearchQueryRequest value) {
    return toSearchRequestBuilder(value).build();
  }

  public SearchRequest.Builder toSearchRequestBuilder(final SearchQueryRequest value) {
    final var sort = value.sort();
    final var searchAfter = value.searchAfter();
    final var searchQuery = value.query();

    final var builder =
        new SearchRequest.Builder().index(value.index()).from(value.from()).size(value.size());

    if (searchQuery != null) {
      final var queryTransformer = getQueryTransformer();
      final var transformedQuery = queryTransformer.apply(searchQuery);
      builder.query(transformedQuery);
    }

    if (sort != null && !sort.isEmpty()) {
      builder.sort(of(sort));
    }

    if (searchAfter != null && searchAfter.length > 0) {
      builder.searchAfter(of(searchAfter));
    }

    if (value.source() != null) {
      builder.source(of(value.source()));
    }

    final var aggregations = value.aggregations();
    if (aggregations != null && !aggregations.isEmpty()) {
      builder.aggregations(
          aggregations.stream()
              .map(
                  aggregation ->
                      Map.entry(
                          aggregation.getName(),
                          transformers
                              .getSearchAggregationTransformer(aggregation.getClass())
                              .apply(aggregation)))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    return builder;
  }

  private List<SortOptions> of(final List<SearchSortOptions> values) {
    final var sortTransformer = getSortOptionsTransformer();
    return values.stream().map(sortTransformer::apply).collect(Collectors.toList());
  }

  private SourceConfig of(final SearchSourceConfig value) {
    final var sourceTransformer = getSourceConfigTransformer();
    return sourceTransformer.apply(value);
  }

  private List<FieldValue> of(final Object[] values) {
    return Arrays.asList(values).stream()
        .map(JsonData::of)
        .map(FieldValue::of)
        .collect(Collectors.toList());
  }
}
