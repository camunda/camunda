/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchCardinalityAggregationTransformerTest {

  private SearchCardinalityAggregationTransformer transformer;

  @BeforeEach
  public void setUp() {
    transformer = new SearchCardinalityAggregationTransformer(new ElasticsearchTransformers());
  }

  @Test
  public void shouldTransformWithField() {
    final SearchCardinalityAggregator aggregator =
        SearchAggregatorBuilders.cardinality().name("cardinalityAgg").field("myField").build();

    final Aggregation aggregation = transformer.apply(aggregator);
    assertThat(aggregation.cardinality()).isNotNull();
    assertThat(aggregation.cardinality().field()).isEqualTo("myField");
  }

  @Test
  public void shouldTransformWithoutField() {
    final SearchCardinalityAggregator aggregator =
        SearchAggregatorBuilders.cardinality().name("cardinalityAgg").build();

    final Aggregation aggregation = transformer.apply(aggregator);
    assertThat(aggregation.cardinality()).isNotNull();
    assertThat(aggregation.cardinality().field()).isNull();
  }
}
