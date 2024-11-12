/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregate.Builder;
import org.opensearch.client.opensearch._types.aggregations.FiltersBucket;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.opensearch.client.util.ObjectBuilder;

public class OpensearchMocks {

  public static SearchResponse<OperationEntity> getMockResponseOf(
      final Function<
              HitsMetadata.Builder<OperationEntity>, ObjectBuilder<HitsMetadata<OperationEntity>>>
          hits,
      final String aggregationName,
      final Function<Builder, ObjectBuilder<Aggregate>> aggregation) {
    return SearchResponse.searchResponseOf(
        r ->
            r.took(3)
                .timedOut(false)
                .shards(s -> s.total(1).failed(0).successful(1))
                .hits(hits)
                .aggregations(aggregationName, aggregation));
  }

  public static Function<
          HitsMetadata.Builder<OperationEntity>, ObjectBuilder<HitsMetadata<OperationEntity>>>
      mockTwoHits(
          final OperationEntity mockResponseEntity1, final OperationEntity mockResponseEntity2) {
    return hits ->
        hits.total(new TotalHits.Builder().value(2).relation(TotalHitsRelation.Eq).build())
            .hits(hit -> hit.id("1").index("operate-operations").source(mockResponseEntity1))
            .hits(hit -> hit.id("2").index("operate-operations").source(mockResponseEntity2));
  }

  public static Function<Builder, ObjectBuilder<Aggregate>> mockTermsAggregationWithSubaggregations(
      final String subAggregationName,
      final Map<String, Function<Builder, ObjectBuilder<Aggregate>>> subAggregations,
      final long docCount) {
    final List<StringTermsBucket> termsBuckets = new ArrayList<>();
    subAggregations.forEach(
        (aggregationKey, aggregation) -> {
          final StringTermsBucket bucket =
              new StringTermsBucket.Builder()
                  .key(aggregationKey)
                  .aggregations(subAggregationName, aggregation)
                  .docCount(docCount)
                  .build();
          termsBuckets.add(bucket);
        });
    return aggs ->
        aggs.sterms(
            t -> t.buckets(buckets -> buckets.array(termsBuckets)).sumOtherDocCount(docCount));
  }

  public static Function<Builder, ObjectBuilder<Aggregate>> mockTwoFilterAggregation(
      final String filter1Name,
      final int filter1DocCount,
      final String filter2Name,
      final int filter2DocCount) {
    return aggs ->
        aggs.filters(
            f ->
                f.buckets(
                    filtersBucketBuilder ->
                        filtersBucketBuilder.keyed(
                            Map.of(
                                filter1Name,
                                FiltersBucket.of(bucket -> bucket.docCount(filter1DocCount)),
                                filter2Name,
                                FiltersBucket.of(bucket -> bucket.docCount(filter2DocCount))))));
  }
}
