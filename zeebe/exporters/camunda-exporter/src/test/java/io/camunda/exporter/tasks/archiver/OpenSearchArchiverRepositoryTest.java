/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.OperationType;
import org.opensearch.client.opensearch.core.search.Hit;
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
  public void shouldNotDeleteWhenMovingIfReindexingFails() throws IOException {
    // given
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error reindexing")));

    // when
    final var future =
        repository.moveDocuments(
            "from-index", "to-index", "key", List.of("1", "2", "3"), Runnable::run);

    // then
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withMessageContaining("Error reindexing");

    verify(client).reindex(any(ReindexRequest.class));
    verify(client)._transport();
    verify(client)._transportOptions();
    verifyNoMoreInteractions(client);
  }

  @Test
  public void shouldReindexThenDeleteWhenMovingDocuments() throws IOException {
    // given
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(ReindexResponse.of(b -> b.total(10L))));
    when(client.deleteByQuery(any(DeleteByQueryRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(DeleteByQueryResponse.of(b -> b.total(10L))));

    // when
    final var future =
        repository.moveDocuments(
            "from-index", "to-index", "key", List.of("1", "2", "3"), Runnable::run);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(client);
    inOrder.verify(client)._transport();
    inOrder.verify(client)._transportOptions();
    inOrder.verify(client).reindex(any(ReindexRequest.class));
    inOrder.verify(client).deleteByQuery(any(DeleteByQueryRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSearchReindexThenNotBulkDeleteWhenMovingDocumentsByIdIfReindexingFails()
      throws IOException {
    // given
    when(client.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(CompletableFuture.completedFuture(searchResponse("4", "5", "6")));
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error reindexing")));

    // when
    final var future =
        repository.moveDocumentsById(
            "from-index",
            "to-index",
            "key",
            List.of("1", "2", "3"),
            Map.of(),
            Map.of(),
            Runnable::run);

    // then
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withMessageContaining("Error reindexing");

    final var inOrder = Mockito.inOrder(client);
    inOrder.verify(client)._transport();
    inOrder.verify(client)._transportOptions();
    inOrder.verify(client).search(any(SearchRequest.class), eq(Object.class));
    inOrder.verify(client).reindex(any(ReindexRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSearchReindexThenBulkDeleteWhenMovingDocumentsById() throws IOException {
    // given
    when(client.search(any(SearchRequest.class), eq(Object.class)))
        .thenReturn(
            CompletableFuture.completedFuture(searchResponse("4", "5", "6")),
            CompletableFuture.completedFuture(searchResponse()));
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(ReindexResponse.of(b -> b.total(3L))));
    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse("4", "5", "6")));

    // when
    final var future =
        repository.moveDocumentsById(
            "from-index",
            "to-index",
            "key",
            List.of("1", "2", "3"),
            Map.of(),
            Map.of(),
            Runnable::run);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(client);
    inOrder.verify(client)._transport();
    inOrder.verify(client)._transportOptions();
    inOrder.verify(client).search(any(SearchRequest.class), eq(Object.class));
    inOrder.verify(client).reindex(any(ReindexRequest.class));
    inOrder.verify(client).bulk(any(BulkRequest.class));
    inOrder.verify(client).search(any(SearchRequest.class), eq(Object.class));
    inOrder.verifyNoMoreInteractions();
  }

  private SearchResponse<Object> searchResponse(final String... ids) {
    final var hits =
        Arrays.stream(ids).map(id -> Hit.<Object>of(h -> h.id(id).index("from-index"))).toList();
    return SearchResponse.searchResponseOf(
        r ->
            r.took(123L)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(
                    h ->
                        h.total(t -> t.value(hits.size()).relation(TotalHitsRelation.Eq))
                            .hits(hits)));
  }

  private BulkResponse bulkResponse(final String... ids) {
    final var items =
        Arrays.stream(ids)
            .map(
                id ->
                    BulkResponseItem.of(
                        h ->
                            h.id(id)
                                .operationType(OperationType.Delete)
                                .index("from-index")
                                .status(200)
                                .result("deleted")))
            .toList();
    return BulkResponse.of(b -> b.took(123L).errors(false).items(items));
  }

  @Test
  void shouldPropagateRoutingForEachBulkDeleteOperation() throws IOException {
    // given
    final var docs =
        List.of(
            new IdWithRouting("4", "routing-4"),
            new IdWithRouting("5", "routing-5"),
            new IdWithRouting("6", "routing-6"));
    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse("4", "5", "6")));

    // when
    final var deleted =
        ((OpenSearchArchiverRepository) repository).deleteDocumentsById("from-index", docs).join();

    // then
    assertThat(deleted).isEqualTo(3L);

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(client).bulk(captor.capture());

    final var operations = captor.getValue().operations();
    assertThat(operations).hasSize(3);
    assertThat(operations.get(0).delete().id()).isEqualTo("4");
    assertThat(operations.get(0).delete().routing()).isEqualTo("routing-4");
    assertThat(operations.get(1).delete().id()).isEqualTo("5");
    assertThat(operations.get(1).delete().routing()).isEqualTo("routing-5");
    assertThat(operations.get(2).delete().id()).isEqualTo("6");
    assertThat(operations.get(2).delete().routing()).isEqualTo("routing-6");
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
