/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.sort.SearchSortOptions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.search.SourceConfig;

public final class SearchRequestTransformer
    extends OpensearchTransformer<SearchQueryRequest, SearchRequest> {

  private final Function<SearchRequest.Builder, SearchRequest.Builder> customizer;

  public SearchRequestTransformer(final OpensearchTransformers transformers) {
    this(transformers, null);
  }

  public SearchRequestTransformer(
      final OpensearchTransformers transformers, final Function<Builder, Builder> customizer) {
    super(transformers);
    this.customizer = customizer;
  }

  public SearchRequestTransformer withSearchRequestCustomizer(
      final Function<SearchRequest.Builder, SearchRequest.Builder> customizer) {
    return new SearchRequestTransformer(transformers, customizer);
  }

  @Override
  public SearchRequest apply(final SearchQueryRequest value) {
    return toSearchRequestBuilder(value).build();
  }

  private Builder toSearchRequestBuilder(final SearchQueryRequest value) {
    final var sort = value.sort();
    final var searchAfter = value.searchAfter();
    final var searchQuery = value.query();

    final var builder = new Builder().index(value.index()).from(value.from()).size(value.size());

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

    applySearchAggregations(value, builder);

    Optional.ofNullable(customizer).ifPresent(customizer -> customizer.apply(builder));

    return builder;
  }

  private void applySearchAggregations(final SearchQueryRequest value, final Builder builder) {
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
  }

  private List<SortOptions> of(final List<SearchSortOptions> values) {
    final var sortTransformer = getSortOptionsTransformer();
    return values.stream().map(sortTransformer::apply).collect(Collectors.toList());
  }

  private List<FieldValue> of(final Object[] values) {
    return Arrays.stream(values).map(Object::toString).map(FieldValue::of).toList();
  }

  private SourceConfig of(final SearchSourceConfig value) {
    final var sourceTransformer = getSourceConfigTransformer();
    return sourceTransformer.apply(value);
  }
}
