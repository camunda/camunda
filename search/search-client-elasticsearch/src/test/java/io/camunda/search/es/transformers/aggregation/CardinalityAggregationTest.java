/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregation.SearchAggregation;
import io.camunda.search.clients.aggregation.SearchAggregationBuilders;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.SearchTransfomer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CardinalityAggregationTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchAggregation, Aggregation> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchAggregation.class);
  }

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregationBuilders.cardinality(c -> c.field("name")).toSearchAggregation(),
            "Aggregation: {'cardinality':{'field':'name'}}"),
        Arguments.arguments(
            SearchAggregationBuilders.cardinality(c -> c.field("name").precisionThreshold(100))
                .toSearchAggregation(),
            "Aggregation: {'cardinality':{'field':'name','precision_threshold':100}}"));
  }

  @ParameterizedTest
  @MethodSource("provideAggregations")
  public void shouldApplyTransformer(
      final SearchAggregation aggregation, final String expectedResultAggregation) {
    // given
    final var expectedAggregation = expectedResultAggregation.replace("'", "\"");

    // when
    final var result = transformer.apply(aggregation);

    // then
    assertThat(result).isNotNull();
    assertThat(result.toString()).isEqualTo(expectedAggregation);
  }
}
