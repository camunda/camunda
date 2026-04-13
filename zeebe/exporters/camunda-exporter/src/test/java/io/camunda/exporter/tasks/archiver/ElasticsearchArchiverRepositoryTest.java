/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesAsyncClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ElasticsearchArchiverRepositoryTest extends AbstractArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepositoryTest.class);

  private final ElasticsearchAsyncClient client = mock(ElasticsearchAsyncClient.class);

  @Override
  @BeforeEach
  void setup() {
    super.setup();
    givenNoArchivingStatus();
  }

  @Override
  void givenSearchRequestsFail() {
    when(client.search(any(SearchRequest.class), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ConnectException("Simulated ES failure for testing")));
  }

  @Override
  void givenNoSearchResultsFound() {
    final var response = mock(SearchResponse.class);
    final var hits = mock(HitsMetadata.class);
    when(response.hits()).thenReturn(hits);
    when(hits.hits()).thenReturn(List.of());
    when(client.search(any(SearchRequest.class), any()))
        .thenReturn(CompletableFuture.completedFuture(response));
  }

  @Override
  ElasticsearchArchiverRepository createRepository() {
    final var metrics = new CamundaExporterMetrics(new SimpleMeterRegistry());
    final var config = new HistoryConfiguration();
    config.setRetention(retention);
    return new ElasticsearchArchiverRepository(
        1,
        config,
        new TestExporterResourceProvider("testPrefix", true),
        client,
        Runnable::run,
        metrics,
        LOGGER);
  }

  @Test
  void shouldCloseTransportOnClose() throws Exception {
    // when
    repository.close();

    // then
    Mockito.verify(client, Mockito.times(1)).close();
  }

  private void givenNoArchivingStatus() {
    final var indicesClient = mock(ElasticsearchIndicesAsyncClient.class);
    when(client.indices()).thenReturn(indicesClient);
    final var response = mock(GetIndexResponse.class);
    when(indicesClient.get(any(Function.class)))
        .thenReturn(CompletableFuture.completedFuture(response));
  }
}
