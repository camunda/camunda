/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.util.ESAggregationSerializer;
import java.io.IOException;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TermsAggregationTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchTermsAggregator, Aggregation> transformer;

  private ESAggregationSerializer esAggregationSerializer;

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(SearchTermsAggregator.class);

    // To serialize OS aggregations to json
    esAggregationSerializer = new ESAggregationSerializer();
  }

  @AfterEach
  public void close() throws Exception {
    esAggregationSerializer.close();
  }

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            new SearchTermsAggregator.Builder()
                .name("A")
                .field("name")
                .size(10)
                .minDocCount(1)
                .build(),
            "{'terms':{'field':'name','min_doc_count':1,'size':10}}"),
        Arguments.arguments(
            new SearchTermsAggregator.Builder()
                .name("A")
                .field("category")
                .size(5)
                .minDocCount(0)
                .build(),
            "{'terms':{'field':'category','min_doc_count':0,'size':5}}"),
        Arguments.arguments(
            new SearchTermsAggregator.Builder()
                .name("A")
                .field("status")
                .size(20)
                .minDocCount(3)
                .build(),
            "{'terms':{'field':'status','min_doc_count':3,'size':20}}"));
  }

  @ParameterizedTest
  @MethodSource("provideAggregations")
  public void shouldApplyTransformer(
      final SearchTermsAggregator aggregation, final String expectedResult) {
    // given
    final var expectedQuery = expectedResult.replace("'", "\"");

    // when
    final var result = transformer.apply(aggregation);

    // then
    assertThat(result).isNotNull();
    Assertions.assertThat(esAggregationSerializer.serialize(result)).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // given

    // when - throw
    assertThatThrownBy(() -> new SearchTermsAggregator.Builder().size(10).build())
        .hasMessageContaining("Expected non-null name for terms aggregation.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // given

    // when - throw
    assertThatThrownBy(() -> new SearchTermsAggregator.Builder().name("A").size(10).build())
        .hasMessageContaining("Expected non-null field for terms aggregation.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnInvalidSize() {
    // given

    // when - throw
    assertThatThrownBy(() -> new SearchTermsAggregator.Builder().field("name").size(-1).build())
        .hasMessageContaining("Size must be a positive integer.")
        .isInstanceOf(IllegalArgumentException.class);
  }
}
