/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.util.OSQuerySerializer;
import io.camunda.search.sort.SearchSortOptions;
import java.io.IOException;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.SortOptions;

public class SortTransfomerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchSortOptions, SortOptions> transformer;

  private OSQuerySerializer osQuerySerializer;

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(SearchSortOptions.class);

    // To serialize OS queries to json
    osQuerySerializer = new OSQuerySerializer();
  }

  @AfterEach
  public void close() throws Exception {
    osQuerySerializer.close();
  }

  private static Stream<Arguments> provideQueryRequests() {
    return Stream.of(
        Arguments.arguments(
            SearchSortOptions.of(builder -> builder.field(f -> f.field("test"))), "{'test':{}}"),
        Arguments.arguments(
            SearchSortOptions.of(builder -> builder.field(f -> f.field("test").asc())),
            "{'test':{'order':'asc'}}"),
        Arguments.arguments(
            SearchSortOptions.of(builder -> builder.field(f -> f.field("test").desc())),
            "{'test':{'order':'desc'}}"),
        Arguments.arguments(
            SearchSortOptions.of(builder -> builder.field(f -> f.field("test").missing("value"))),
            "{'test':{'missing':'value'}}"),
        Arguments.arguments(
            SearchSortOptions.of(
                builder -> builder.field(f -> f.field("test").missing("value").desc())),
            "{'test':{'missing':'value','order':'desc'}}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueryRequests")
  public void shouldApplyTransformer(
      final SearchSortOptions sortOptions, final String expectedResult) {
    // given
    final var expectedQuery = expectedResult.replace("'", "\"");

    // when
    final var result = transformer.apply(sortOptions);

    // then
    assertThat(result).isNotNull();
    Assertions.assertThat(osQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
  }
}
