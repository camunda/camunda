/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.camunda.search.clients.aggregation.SearchAggregate;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class SearchResponseTransformer<T>
    extends ElasticsearchTransformer<SearchResponse<T>, SearchQueryResponse<T>> {

  public SearchResponseTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchQueryResponse<T> apply(final SearchResponse<T> value) {
    final var hits = value.hits();
    final var scrollId = value.scrollId();

    final var total = hits.total();
    final var totalHits = of(total);

    final var sourceHits = hits.hits();
    final var transformedHits = of(sourceHits);
    final var transformedAggregates = of(value.aggregations());

    return new SearchQueryResponse.Builder<T>()
        .totalHits(totalHits)
        .scrollId(scrollId)
        .hits(transformedHits)
        .aggregations(transformedAggregates)
        .build();
  }

  private List<SearchQueryHit<T>> of(final List<Hit<T>> hits) {
    if (hits != null) {
      final var hitTransformer = new SearchQueryHitTransformer<T>(transformers);
      return hits.stream().map(hitTransformer::apply).collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private long of(final TotalHits totalHits) {
    if (totalHits != null) {
      return totalHits.value();
    }
    return 0;
  }

  private Map<String, SearchAggregate> of(
      final Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> values) {
    final var aggregateTransformer = getAggregateTransformer();
    return values.entrySet().stream()
        .collect(
            Collectors.toMap(Entry::getKey, entry -> aggregateTransformer.apply(entry.getValue())));
  }
}
