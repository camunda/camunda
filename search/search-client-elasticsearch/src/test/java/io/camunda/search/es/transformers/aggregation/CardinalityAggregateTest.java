/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.AggregateBuilders;
import io.camunda.search.clients.aggregation.SearchAggregate;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.SearchTransfomer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CardinalityAggregateTest {
  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<Aggregate, SearchAggregate> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(Aggregate.class);
  }

  private static Stream<Arguments> provideAggregates() {
    return Stream.of(
        Arguments.arguments(
            AggregateBuilders.cardinality(c -> c.value(23)),
            "SearchAggregate[aggregateOption=SearchCardinalityAggregate[value=23]]"));
  }

  @ParameterizedTest
  @MethodSource("provideAggregates")
  public void shouldApplyTransformer(
      final Aggregate aggregate, final String expectedResultAggregate) {
    // given
    final var expectedAggregate = expectedResultAggregate.replace("'", "\"");

    // when
    final var result = transformer.apply(aggregate);

    // then
    assertThat(result).isNotNull();
    assertThat(result.toString()).isEqualTo(expectedAggregate);
  }
}
