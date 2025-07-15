/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.aggregations.SingleBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TopHitsAggregate;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.AggregationResult.Builder;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.clients.transformers.query.Cursor;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.transformers.search.SearchQueryHitTransformer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SearchAggregationResultTransformer<T>
    implements SearchTransfomer<Map<String, Aggregate>, Map<String, AggregationResult>> {

  private static final String COMPOSITE_KEY_DELIMITER = "__";
  private final ElasticsearchTransformers transformers;
  private final List<SearchAggregator> aggregators;

  public SearchAggregationResultTransformer(
      final ElasticsearchTransformers transformers, final List<SearchAggregator> aggregators) {
    this.transformers = transformers;
    this.aggregators = aggregators;
  }

  @Override
  public Map<String, AggregationResult> apply(final Map<String, Aggregate> value) {
    return transformAggregation(value);
  }

  private AggregationResult transformSingleBucketAggregate(
      final SingleBucketAggregateBase aggregate) {
    return new Builder()
        .docCount(aggregate.docCount())
        .aggregations(transformAggregation(aggregate.aggregations()))
        .build();
  }

  private SearchTopHitsAggregator findTopHitsAggregatorRecursively(
      final List<SearchAggregator> aggregators, final String key) {
    if (aggregators == null || aggregators.isEmpty()) {
      return null;
    }

    // First try to find direct match
    final Optional<SearchTopHitsAggregator> directMatch =
        aggregators.stream()
            .filter(SearchTopHitsAggregator.class::isInstance)
            .map(SearchTopHitsAggregator.class::cast)
            .filter(aggregator -> aggregator.getName().equals(key))
            .findFirst();

    // If not found, search recursively in sub-aggregations
    return directMatch.orElseGet(
        () ->
            aggregators.stream()
                .map(
                    aggregator ->
                        findTopHitsAggregatorRecursively(aggregator.getAggregations(), key))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null));
  }

  private AggregationResult transformTopHitsAggregate(
      final String key, final TopHitsAggregate aggregate) {
    final var hits = aggregate.hits().hits();

    final var topHitAggregator = findTopHitsAggregatorRecursively(aggregators, key);

    return new Builder().hits(toSearchQueryHits(hits, topHitAggregator.documentClass())).build();
  }

  private <B extends MultiBucketBase> AggregationResult transformMultiBucketAggregate(
      final MultiBucketAggregateBase<B> aggregate) {
    final var map = new HashMap<String, AggregationResult>();
    final var buckets = aggregate.buckets();
    final var searchAfter = extractSearchAfter(aggregate);
    if (buckets.isKeyed()) {
      buckets
          .keyed()
          .forEach(
              (key, bucket) -> {
                final var result =
                    new Builder()
                        .docCount(bucket.docCount())
                        .aggregations(transformAggregation(bucket.aggregations()))
                        .build();
                map.put(key, result);
              });
    } else if (buckets.isArray()) {
      final List<B> array = buckets.array();
      array.forEach(
          bucket -> {
            final String key =
                switch (bucket) {
                  case final StringTermsBucket b -> b.key().stringValue();
                  case final LongTermsBucket b -> b.keyAsString();
                  case final CompositeBucket b ->
                      b.key().values().stream()
                          .map(FieldValue::stringValue)
                          .collect(Collectors.joining(COMPOSITE_KEY_DELIMITER));
                  default ->
                      throw new IllegalStateException(
                          "Unsupported bucket type: " + bucket.getClass());
                };
            final var result =
                new Builder()
                    .docCount(bucket.docCount())
                    .aggregations(transformAggregation(bucket.aggregations()))
                    .build();
            map.put(key, result);
          });
    }
    return new Builder().aggregations(map).endCursor(Cursor.encode(searchAfter)).build();
  }

  private <B extends MultiBucketBase> Object[] extractSearchAfter(
      final MultiBucketAggregateBase<B> aggregate) {
    if (aggregate instanceof final CompositeAggregate compositeAggregate) {
      return compositeAggregate.afterKey() != null
          ? compositeAggregate.afterKey().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stringValue()))
              .entrySet()
              .toArray()
          : null;
    }
    return null;
  }

  private Map<String, AggregationResult> transformAggregation(
      final Map<String, Aggregate> aggregations) {
    if (aggregations.isEmpty()) {
      return null;
    }

    final var result = new HashMap<String, AggregationResult>();
    aggregations.forEach(
        (key, aggregate) -> {
          final AggregationResult res;
          switch (Objects.requireNonNull(aggregate._kind())) {
            case Children -> res = transformSingleBucketAggregate(aggregate.children());
            case Parent -> res = transformSingleBucketAggregate(aggregate.parent());
            case Filter -> res = transformSingleBucketAggregate(aggregate.filter());
            case Filters -> res = transformMultiBucketAggregate(aggregate.filters());
            case Sterms -> res = transformMultiBucketAggregate(aggregate.sterms());
            case Composite -> res = transformMultiBucketAggregate(aggregate.composite());
            case TopHits -> res = transformTopHitsAggregate(key, aggregate.topHits());
            default ->
                throw new IllegalStateException(
                    "Unsupported aggregation type: " + aggregate._kind());
          }
          result.put(key, res);
        });
    return result;
  }

  private List<SearchQueryHit> toSearchQueryHits(
      final List<Hit<JsonData>> hits, final Class<T> documentClass) {
    if (hits != null) {
      final var hitTransformer = new SearchQueryHitTransformer<T>(transformers);
      return hits.stream()
          .filter(hit -> Objects.nonNull(hit.source()))
          .map(
              hit ->
                  new Hit.Builder<T>()
                      .index(hit.index())
                      .id(hit.id())
                      .score(hit.score())
                      .source(hit.source().to(documentClass))
                      .build())
          .map(hitTransformer::apply)
          .collect(Collectors.toList());
    }
    return List.of();
  }
}
