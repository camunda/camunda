/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MinMaxStatsServiceESTest {

  private static final String[] INDICES = {"some-index"};

  private ElasticsearchClient elasticsearchClient;
  private MinMaxStatsServiceES underTest;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws IOException {
    final OptimizeIndexNameService indexNameService = mock(OptimizeIndexNameService.class);
    when(indexNameService.getOptimizeIndexAliasForIndex(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    elasticsearchClient = mock(ElasticsearchClient.class, RETURNS_DEEP_STUBS);
    when(elasticsearchClient.withTransportOptions(any(TransportOptions.class)))
        .thenReturn(elasticsearchClient);
    underTest =
        new MinMaxStatsServiceES(
            new OptimizeElasticsearchClient(
                mock(Rest5Client.class),
                new ObjectMapper(),
                elasticsearchClient,
                indexNameService));

    // ES returns stats aggregations without min/max (null in the ES9 client) when nothing matched
    final Aggregate emptyStats = Aggregate.of(a -> a.stats(s -> s.count(0).sum(0.0)));
    final SearchResponse<Object> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("statsAggField1", emptyStats, "statsAggField2", emptyStats));
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(response);
  }

  @Test
  void shouldReturnEmptyStatsForScriptedFieldWhenNoDocumentMatches() {
    // given
    final Script script = Script.of(s -> s.source(src -> src.scriptString("1")));

    // when
    final MinMaxStatDto result =
        underTest.getScriptedMinMaxStats(Query.of(q -> q.matchAll(m -> m)), INDICES, null, script);

    // then
    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void shouldReturnEmptyStatsForSingleFieldWhenNoDocumentMatches() {
    // when
    final MinMaxStatDto result =
        underTest.getSingleFieldMinMaxStats(
            Query.of(q -> q.matchAll(m -> m)), INDICES, "someField", null, null);

    // then
    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldUseNonEmptyFieldStatsWhenOnlyOneOfTwoFieldsHasData() throws IOException {
    // given
    final Aggregate emptyStats = Aggregate.of(a -> a.stats(s -> s.count(0).sum(0.0)));
    final Aggregate stats =
        Aggregate.of(a -> a.stats(s -> s.count(2).min(5.0).max(10.0).avg(7.5).sum(15.0)));
    final SearchResponse<Object> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("statsAggField1", stats, "statsAggField2", emptyStats));
    when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(response);

    // when
    final MinMaxStatDto result =
        underTest.getSingleFieldMinMaxStats(
            Query.of(q -> q.matchAll(m -> m)), INDICES, "someField", null, null);

    // then
    assertThat(result.getMin()).isEqualTo(5.0);
    assertThat(result.getMax()).isEqualTo(10.0);
  }
}
