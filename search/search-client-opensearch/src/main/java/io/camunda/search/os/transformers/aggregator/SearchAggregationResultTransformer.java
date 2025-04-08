/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.AggregationResult.Builder;
import io.camunda.search.clients.transformers.SearchTransfomer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketAggregateBase;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketBase;
import org.opensearch.client.opensearch._types.aggregations.SingleBucketAggregateBase;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;

public class SearchAggregationResultTransformer
    implements SearchTransfomer<Map<String, Aggregate>, Map<String, AggregationResult>> {

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

  private <B extends MultiBucketBase> AggregationResult transformMultiBucketAggregate(
      final MultiBucketAggregateBase<B> aggregate) {
    final var map = new HashMap<String, AggregationResult>();
    final var buckets = aggregate.buckets();
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
                  case final StringTermsBucket b -> b.key();
                  case final LongTermsBucket b -> b.keyAsString();
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
    return new Builder().aggregations(map).build();
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
            default ->
                throw new IllegalStateException(
                    "Unsupported aggregation type: " + aggregate._kind());
          }
          result.put(key, res);
        });
    return result;
  }
}
