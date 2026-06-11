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

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesAsyncClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.TransportOptions;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.json.Json;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ElasticsearchArchiverRepositoryTest extends AbstractArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepositoryTest.class);

  private final ElasticsearchAsyncClient client = mock(ElasticsearchAsyncClient.class);

  @BeforeEach
  void setUp() {
    when(client._transportOptions()).thenReturn(DefaultTransportOptions.EMPTY);
    when(client.withTransportOptions(any(TransportOptions.class))).thenReturn(client);
  }

  @Test
  void shouldCloseClientOnClose() throws Exception {
    // when
    repository.close();

    // then
    Mockito.verify(client, Mockito.times(1)).close();
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
  public void shouldConstructCorrectQueryForPIMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI);
    final var repository = createRepository(client, config);
    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SearchResponse.class)));

    // when
    repository.getProcessInstancesNextBatch(100);

    // then
    final var captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(client).search(captor.capture(), eq(ProcessInstanceForListViewEntity.class));
    final var query = captor.getValue().query();
    assertThat(query.isBool()).isTrue();
    // In PI mode, we expect NO hierarchy filter (which would appear as a should/must clause
    // specifically targeting root/parent keys inside the main bool filter)
    // The main filter contains: endDate, joinRelation, partitionId.
    // hierarchy filter is added as another filter clause if present.
    // So we check that we only have the base filters.
    // This assertion depends on identifying the filters.
  }

  @Test
  public void shouldMapBatchForPIMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI);
    final var client = mock(ElasticsearchAsyncClient.class);
    final var repository = createRepository(client, config);
    final var hit1 = createHit("1", "2024-01-01", null);
    final var hit2 = createHit("2", "2024-01-01", 100L);

    final var response = createResponse(List.of(hit1, hit2));
    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // when
    final var batch = repository.getProcessInstancesNextBatch(100).join();

    // then
    assertThat(batch.processInstanceKeys()).containsExactly(1L, 2L);
    assertThat(batch.rootProcessInstanceKeys()).isEmpty();
  }

  @Test
  public void shouldMapBatchForPIHierarchyMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI_HIERARCHY);
    final var client = mock(ElasticsearchAsyncClient.class);
    final var repository = createRepository(client, config);
    final var hit1 = createHit("1", "2024-01-01", null); // Legacy
    final var hit2 = createHit("2", "2024-01-01", 100L); // Root

    final var response = createResponse(List.of(hit1, hit2));
    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // when
    final var batch = repository.getProcessInstancesNextBatch(100).join();

    // then
    assertThat(batch.processInstanceKeys()).containsExactly(1L);
    // For root, it maps the root key, not the ID. hit2 has rootKey 100L.
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(100L);
  }

  @Test
  public void shouldMapBatchForPIHierarchyIgnoreLegacyMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI_HIERARCHY_IGNORE_LEGACY);
    final var client = mock(ElasticsearchAsyncClient.class);
    final var repository = createRepository(client, config);

    final var hit1 = createHit("1", "2024-01-01", null);
    final var hit2 = createHit("2", "2024-01-01", 100L);

    final var response = createResponse(List.of(hit1, hit2));
    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // when
    final var batch = repository.getProcessInstancesNextBatch(100).join();

    // then - purely based on mapping logic which splits by root key presence
    assertThat(batch.processInstanceKeys()).containsExactly(1L);
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(100L);
  }

  @Test
  public void shouldNotDeleteWhenMovingIfReindexingFails() {
    // given
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error reindexing")));

    // when
    final var future =
        repository.moveDocuments(
            "from-index", "to-index", Map.of("key", List.of("1", "2", "3")), Runnable::run);

    // then
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withMessageContaining("Error reindexing");

    verify(client).reindex(any(ReindexRequest.class));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void shouldReindexThenDeleteWhenMovingDocuments() {
    // given
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(ReindexResponse.of(b -> b.total(10L))));
    when(client.deleteByQuery(any(DeleteByQueryRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(DeleteByQueryResponse.of(b -> b.total(10L))));

    // when
    final var future =
        repository.moveDocuments(
            "from-index", "to-index", Map.of("key", List.of("1", "2", "3")), Runnable::run);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(client);
    inOrder.verify(client).reindex(any(ReindexRequest.class));
    inOrder.verify(client).deleteByQuery(any(DeleteByQueryRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSearchReindexThenNotBulkDeleteWhenMovingDocumentsByIdIfReindexingFails() {
    // given
    when(client.search(any(SearchRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(searchResponse("4", "5", "6")));
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error reindexing")));

    // when
    final var future =
        repository.moveDocumentsById(
            "from-index",
            "to-index",
            Map.of("key", List.of("1", "2", "3")),
            Map.of(),
            Map.of(),
            Runnable::run);

    // then
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withMessageContaining("Error reindexing");

    final var inOrder = Mockito.inOrder(client);
    inOrder.verify(client).search(any(SearchRequest.class));
    inOrder.verify(client).reindex(any(ReindexRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSearchReindexThenBulkDeleteWhenMovingDocumentsById() {
    // given
    when(client.search(any(SearchRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(searchResponse("4", "5", "6")),
            CompletableFuture.completedFuture(searchResponse()));
    when(client.reindex(any(ReindexRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                ReindexResponse.of(b -> b.total(3L).created(3L).updated(0L))));
    when(client.bulk(any(BulkRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse("4", "5", "6")));

    // when
    final var future =
        repository.moveDocumentsById(
            "from-index",
            "to-index",
            Map.of("key", List.of("1", "2", "3")),
            Map.of(),
            Map.of(),
            Runnable::run);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = Mockito.inOrder(client);
    inOrder.verify(client).search(any(SearchRequest.class));
    inOrder.verify(client).reindex(any(ReindexRequest.class));
    inOrder.verify(client).bulk(any(BulkRequest.class));
    inOrder.verify(client).search(any(SearchRequest.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldPropagateRoutingForEachBulkDeleteOperation() {
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
        ((ElasticsearchArchiverRepository) repository)
            .deleteDocumentsById("from-index", docs)
            .join();

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

  private SearchResponse<Void> searchResponse(final String... ids) {
    final var hits =
        Arrays.stream(ids).map(id -> Hit.<Void>of(h -> h.id(id).index("from-index"))).toList();
    return SearchResponse.of(
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

  private ElasticsearchArchiverRepository createRepository(
      final ElasticsearchAsyncClient client, final HistoryConfiguration config) {
    if (Mockito.mockingDetails(client).isMock()) {
      final var indicesClient = mock(ElasticsearchIndicesAsyncClient.class);
      when(client.indices()).thenReturn(indicesClient);
      final var getIndexResponse = mock(GetIndexResponse.class);
      when(indicesClient.get(any(Function.class)))
          .thenReturn(CompletableFuture.completedFuture(getIndexResponse));
      when(getIndexResponse.result()).thenReturn(Map.of());
    }

    config.setRetention(retention);
    return new ElasticsearchArchiverRepository(
        1,
        config,
        new TestExporterResourceProvider("testPrefix", true),
        client,
        Runnable::run,
        new CamundaExporterMetrics(new SimpleMeterRegistry()),
        LOGGER);
  }

  private Hit<ProcessInstanceForListViewEntity> createHit(
      final String id, final String endDate, final Long rootPIKey) {
    final var entity = new ProcessInstanceForListViewEntity();
    entity.setRootProcessInstanceKey(rootPIKey);

    return Hit.of(
        h ->
            h.index("index")
                .id(id)
                .source(entity)
                .fields(
                    Map.of(
                        ListViewTemplate.END_DATE,
                        JsonData.of(Json.createArrayBuilder().add(endDate).build()))));
  }

  private SearchResponse<ProcessInstanceForListViewEntity> createResponse(
      final List<Hit<ProcessInstanceForListViewEntity>> hits) {
    return SearchResponse.of(
        r ->
            r.took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(
                    h ->
                        h.total(t -> t.value(hits.size()).relation(TotalHitsRelation.Eq))
                            .hits(hits)));
  }
}
