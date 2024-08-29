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
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.JsonData;
import io.camunda.search.clients.aggregation.SearchAggregation;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
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
    final var sort = value.sort();
    final var searchAfter = value.searchAfter();
    final var searchQuery = value.query();
    final var aggregations = value.aggregations();

    final var builder =
        new SearchRequest.Builder().index(value.index()).from(value.from()).size(value.size());

    if (searchQuery != null) {
      final var queryTransformer = getQueryTransformer();
      final var transformedQuery = queryTransformer.apply(searchQuery);
      builder.query(transformedQuery);
    }

    if (aggregations != null && !aggregations.isEmpty()) {
      builder.aggregations(of(aggregations));
    }

    if (sort != null && !sort.isEmpty()) {
      builder.sort(of(sort));
    }

    if (searchAfter != null && searchAfter.length > 0) {
      builder.searchAfter(of(searchAfter));
    }

    return builder.build();
  }

  private List<SortOptions> of(final List<SearchSortOptions> values) {
    final var sortTransformer = getSortOptionsTransformer();
    return values.stream().map(sortTransformer::apply).collect(Collectors.toList());
  }

  private List<FieldValue> of(final Object[] values) {
    return Arrays.asList(values).stream()
        .map(JsonData::of)
        .map(FieldValue::of)
        .collect(Collectors.toList());
  }

  private Map<String, Aggregation> of(final Map<String, SearchAggregation> values) {
    final var aggregationTransformer = getAggregationTransformer();
    return values.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> aggregationTransformer.apply(entry.getValue())));
  }
}
