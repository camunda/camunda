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

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesAsyncClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenSearchArchiverRepositoryTest extends AbstractArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchArchiverRepositoryTest.class);

  private final OpenSearchTransport transport = mock(OpenSearchTransport.class);
  private final OpenSearchAsyncClient client = mock(OpenSearchAsyncClient.class);

  @Override
  @BeforeEach
  void setup() {
    super.setup();
    when(client._transport()).thenReturn(transport);
    givenNoArchivingStatus();
  }

  @Override
  void givenSearchRequestsFail() {
    try {
      when(client.search(any(SearchRequest.class), any()))
          .thenReturn(CompletableFuture.failedFuture(new ConnectException("Connection failed")));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  void givenNoSearchResultsFound() {
    try {
      final var response =
          new SearchResponse.Builder<>()
              .took(1)
              .timedOut(false)
              .shards(s -> s.total(1).successful(1).failed(0))
              .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq)).hits(List.of()))
              .build();
      when(client.search(any(SearchRequest.class), any()))
          .thenReturn(CompletableFuture.completedFuture(response));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  OpenSearchArchiverRepository createRepository() {
    final var metrics = new CamundaExporterMetrics(new SimpleMeterRegistry());
    final var config = new HistoryConfiguration();
    config.setRetention(retention);

    return new OpenSearchArchiverRepository(
        1,
        config,
        new TestExporterResourceProvider("testPrefix", false),
        client,
        new OpenSearchGenericClient(client._transport(), client._transportOptions()),
        Runnable::run,
        metrics,
        LOGGER);
  }

  @Test
  void shouldCloseTransportOnClose() throws Exception {
    // when
    repository.close();

    // then
    Mockito.verify(transport, Mockito.times(1)).close();
  }

  private void givenNoArchivingStatus() {
    try {
      final var indicesClient = mock(OpenSearchIndicesAsyncClient.class);
      when(client.indices()).thenReturn(indicesClient);
      final var response = mock(GetIndexResponse.class);
      when(indicesClient.get(any(Function.class)))
          .thenReturn(CompletableFuture.completedFuture(response));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
