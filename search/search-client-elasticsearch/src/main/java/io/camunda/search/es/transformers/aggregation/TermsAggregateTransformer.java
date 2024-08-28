/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import io.camunda.search.clients.aggregation.Bucket;
import io.camunda.search.clients.aggregation.SearchAggregate;
import io.camunda.search.clients.aggregation.SearchAggregateBuilders;
import io.camunda.search.clients.aggregation.SearchTermsAggregate;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class TermsAggregateTransformer
    extends AggregateOptionTransformer<LongTermsAggregate, SearchTermsAggregate> {

  public TermsAggregateTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchTermsAggregate apply(final LongTermsAggregate value) {
    final var transformedBuckets = of(value.buckets().array());
    return SearchAggregateBuilders.terms().buckets(transformedBuckets).build();
  }

  public List<Bucket> of(final List<LongTermsBucket> buckets) {
    return buckets.stream()
        .map(
            b ->
                new Bucket(
                    b.keyAsString(), b.docCount(), getTransformedAggregates(b.aggregations())))
        .toList();
  }

  private Map<String, SearchAggregate> getTransformedAggregates(
      final Map<String, Aggregate> aggregates) {
    final var aggregateTransformer = getAggregateTransformer();
    return aggregates.entrySet().stream()
        .collect(
            Collectors.toMap(Entry::getKey, entry -> aggregateTransformer.apply(entry.getValue())));
  }
}
