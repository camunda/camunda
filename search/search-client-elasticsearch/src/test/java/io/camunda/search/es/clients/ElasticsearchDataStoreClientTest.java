/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import static io.camunda.search.clients.aggregation.SearchAggregationBuilders.cardinality;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregation;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import io.camunda.search.clients.aggregation.SearchAggregate;
import io.camunda.search.clients.aggregation.SearchCardinalityAggregate;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.es.util.StubbedElasticsearchClient;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ElasticsearchDataStoreClientTest {

  private ElasticsearchSearchClient client;
  private StubbedElasticsearchClient stubbedElasticsearchClient;

  @BeforeEach
  public void before() {
    stubbedElasticsearchClient = new StubbedElasticsearchClient();
    stubbedElasticsearchClient.registerHandler(
        (h) -> {
          return SearchResponse.of(
              (f) ->
                  f.took(122)
                      .hits(
                          HitsMetadata.of(
                              (m) ->
                                  m.hits(new ArrayList<>())
                                      .total((t) -> t.value(789).relation(TotalHitsRelation.Eq))))
                      .aggregations(
                          "test-aggregate", Aggregate.of(a -> a.cardinality(c -> c.value(23))))
                      .shards((s) -> s.failed(0).successful(100).total(100))
                      .timedOut(false));
        });

    client = new ElasticsearchSearchClient(stubbedElasticsearchClient);
  }

  @Test
  public void shouldTransformSearchRequest() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(
            b ->
                b.index("operate-list-view-8.3.0_")
                    .size(1)
                    .aggregations(
                        Map.of(
                            "test-aggregate",
                            cardinality(c -> c.field("count")).toSearchAggregation())));

    // when
    client.search(request, Object.class);

    // then
    final var searchRequest = stubbedElasticsearchClient.getSingleSearchRequest();
    assertThat(searchRequest.index()).hasSize(1).contains("operate-list-view-8.3.0_");
    assertThat(searchRequest.aggregations()).hasSize(1);
    assertIsAggregation(
        searchRequest.aggregations().get("test-aggregate"), CardinalityAggregation.class);
  }

  @Test
  public void shouldTransformSearchResponse() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    final var response = client.search(request, Object.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.get().totalHits()).isEqualTo(789);
    assertThat(response.get().aggregations()).hasSize(1);
    assertIsAggregate(
        response.get().aggregations().get("test-aggregate"), SearchCardinalityAggregate.class);
  }

  private void assertIsAggregation(
      final Aggregation aggregation, final Class<?> expectedAggregationVariantClass) {
    assertThat(aggregation).isInstanceOf(Aggregation.class);
    assertThat(aggregation._get()).isInstanceOf(expectedAggregationVariantClass);
  }

  private void assertIsAggregate(
      final SearchAggregate searchAggregate, final Class<?> expectedAggregateOptionClass) {
    assertThat(searchAggregate).isInstanceOf(SearchAggregate.class);
    assertThat(searchAggregate.aggregateOption()).isInstanceOf(expectedAggregateOptionClass);
  }
}
