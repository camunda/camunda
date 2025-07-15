/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.transformers.aggregator.SearchAggregationResultTransformer;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

public final class SearchResponseTransformer<T>
    extends OpensearchTransformer<
        Tuple<SearchResponse<T>, List<SearchAggregator>>, SearchQueryResponse<T>> {

  public SearchResponseTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchQueryResponse<T> apply(
      final Tuple<SearchResponse<T>, List<SearchAggregator>> tuple) {
    final var value = tuple.getLeft();
    final var aggregators = tuple.getRight();
    final var hits = value.hits();
    final var scrollId = value.scrollId();
    final var responseAggregations = value.aggregations();

    final var total = hits.total();
    final var totalHits = of(total);
    final var hasMoreTotalItems =
        Objects.nonNull(total) && total.relation() == TotalHitsRelation.Gte;

    final var sourceHits = hits.hits();
    final var transformedHits = of(sourceHits);
    final var transformedAggregations = of(responseAggregations, aggregators);

    return new SearchQueryResponse.Builder<T>()
        .totalHits(totalHits, hasMoreTotalItems)
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

  private Map<String, AggregationResult> of(
      final Map<String, Aggregate> value, final List<SearchAggregator> aggregators) {
    if (value != null) {
      return new SearchAggregationResultTransformer(transformers, aggregators).apply(value);
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
