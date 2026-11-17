/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_LATEST_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProcessDefinitionLatestVersionAggregationTransformerTest {

  private static SearchTopHitsAggregator<?> latestDefinitionAggregator(
      final ProcessDefinitionLatestVersionAggregation aggregation) {
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(ProcessDefinitionLatestVersionAggregation.class);

    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    return aggregators.getFirst().getAggregations().stream()
        .filter(a -> a.getName().equals(AGGREGATION_NAME_LATEST_DEFINITION))
        .map(a -> (SearchTopHitsAggregator<?>) a)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void shouldExcludeBpmnXmlFromLatestDefinitionByDefault() {
    // given
    final var aggregation =
        (ProcessDefinitionLatestVersionAggregation)
            ProcessDefinitionQuery.of(q -> q.filter(f -> f.isLatestVersion(true))).aggregation();

    // when
    final var latestDefinitionAggregator = latestDefinitionAggregator(aggregation);

    // then
    assertThat(latestDefinitionAggregator.excludes()).containsExactly("bpmnXml");
  }

  @Test
  void shouldIncludeBpmnXmlInLatestDefinitionWhenRequested() {
    // given
    final var aggregation =
        (ProcessDefinitionLatestVersionAggregation)
            ProcessDefinitionQuery.of(
                    q ->
                        q.filter(f -> f.isLatestVersion(true))
                            .resultConfig(r -> r.includeXml(true)))
                .aggregation();

    // when
    final var latestDefinitionAggregator = latestDefinitionAggregator(aggregation);

    // then
    assertThat(latestDefinitionAggregator.excludes()).isEmpty();
  }
}
