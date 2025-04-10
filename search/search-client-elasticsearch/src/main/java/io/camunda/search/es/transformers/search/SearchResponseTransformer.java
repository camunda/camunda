/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.transformers.aggregator.SearchAggregationResultTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    final var aggregations = value.aggregations();

    final var total = hits.total();
    final var totalHits = of(total);

    final var sourceHits = hits.hits();
    final var transformedHits = of(sourceHits);
    final var transformedAggregations = of(aggregations);

    return new SearchQueryResponse.Builder<T>()
        .totalHits(totalHits)
        .scrollId(scrollId)
        .hits(transformedHits)
        .aggregations(transformedAggregations)
        .build();
  }

  private List<SearchQueryHit<T>> of(final List<Hit<T>> hits) {
    if (hits != null) {
      final var hitTransformer = new SearchQueryHitTransformer<T>(transformers);
      return hits.stream().map(hitTransformer::apply).collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private Map<String, AggregationResult> of(final Map<String, Aggregate> value) {
    if (value != null) {
      return new SearchAggregationResultTransformer().apply(value);
    }
    return null;
  }

  private long of(final TotalHits totalHits) {
    if (totalHits != null) {
      return totalHits.value();
    }
    return 0;
  }
}
