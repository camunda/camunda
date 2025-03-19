/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.util.OSAggregationSerializer;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public abstract class AbstractSearchAggregatorTransformerTest<T extends SearchAggregator> {

  protected final OpensearchTransformers transformers = new OpensearchTransformers();
  protected SearchTransfomer<T, Aggregation> transformer;

  protected OSAggregationSerializer osAggregationSerializer;

  protected abstract Class<T> getTransformerClass();

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(getTransformerClass());

    // To serialize OS aggregations to json
    osAggregationSerializer = new OSAggregationSerializer();
  }

  @AfterEach
  public void close() throws Exception {
    osAggregationSerializer.close();
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
    Assertions.assertThat(osAggregationSerializer.serialize(result)).isEqualTo(expectedQuery);
  }
}
