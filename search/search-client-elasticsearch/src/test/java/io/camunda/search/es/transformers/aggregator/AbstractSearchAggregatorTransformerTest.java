/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.util.ESAggregationSerializer;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractSearchAggregatorTransformerTest<T extends SearchAggregator> {

  protected final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  protected SearchTransfomer<T, Aggregation> transformer;

  protected ESAggregationSerializer esAggregationSerializer;

  protected abstract Class<T> getTransformerClass();

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(getTransformerClass());

    // To serialize OS aggregations to json
    esAggregationSerializer = new ESAggregationSerializer();
  }

  @AfterEach
  public void close() throws Exception {
    esAggregationSerializer.close();
  }

  @ParameterizedTest
  @MethodSource("provideAggregations")
  public void shouldApplyTransformer(final T aggregation, final String expectedResult) {
    // given
    final var expectedQuery = expectedResult.replace("'", "\"");

    // when
    final var result = transformer.apply(aggregation);

    // then
    assertThat(result).isNotNull();
    Assertions.assertThat(esAggregationSerializer.serialize(result)).isEqualTo(expectedQuery);
  }
}
