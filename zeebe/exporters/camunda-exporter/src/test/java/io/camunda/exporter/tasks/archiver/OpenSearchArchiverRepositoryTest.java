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
import static org.mockito.Mockito.when;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.json.Json;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
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

  @Test
  public void shouldConstructCorrectQueryForPIMode() throws IOException {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI);
    final var client = mock(OpenSearchAsyncClient.class);
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
  }

  @Test
  public void shouldMapBatchForPIMode() throws IOException {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI);
    final var client = mock(OpenSearchAsyncClient.class);
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
  public void shouldMapBatchForPIHierarchyMode() throws IOException {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI_HIERARCHY);
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
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(100L);
  }

  @Test
  public void shouldMapBatchForPIHierarchyIgnoreLegacyMode() throws IOException {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI_HIERARCHY_IGNORE_LEGACY);
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

  private OpenSearchArchiverRepository createRepository(
      final OpenSearchAsyncClient client, final HistoryConfiguration config) throws IOException {
    if (Mockito.mockingDetails(client).isMock()) {
      final var indicesClient = mock(OpenSearchIndicesAsyncClient.class);
      when(client.indices()).thenReturn(indicesClient);
      final var getIndexResponse = mock(GetIndexResponse.class);
      when(indicesClient.get(any(Function.class)))
          .thenReturn(CompletableFuture.completedFuture(getIndexResponse));
      when(getIndexResponse.result()).thenReturn(Map.of());
    }

    config.setRetention(retention);
    return new OpenSearchArchiverRepository(
        1,
        config,
        new TestExporterResourceProvider("testPrefix", false),
        client,
        mock(OpenSearchGenericClient.class),
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
    return new SearchResponse.Builder<ProcessInstanceForListViewEntity>()
        .took(1)
        .timedOut(false)
        .shards(s -> s.total(1).successful(1).failed(0))
        .hits(h -> h.total(t -> t.value(hits.size()).relation(TotalHitsRelation.Eq)).hits(hits))
        .build();
  }
}
