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

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenSearchArchiverRepositoryTest extends AbstractArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchArchiverRepositoryTest.class);

  private final RestClientTransport transport = Mockito.spy(createRestClient());

  @Test
  void shouldCloseTransportOnClose() throws Exception {
    // when
    repository.close();

    // then
    Mockito.verify(transport, Mockito.times(1)).close();
  }

  @Override
  OpenSearchArchiverRepository createRepository() {
    final var client = new OpenSearchAsyncClient(transport);
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

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create("http://127.0.0.1:1")).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }

  @Test
  public void shouldConstructCorrectQueryForPIMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setRetentionMode(RetentionMode.PI);
    final var client = mock(OpenSearchAsyncClient.class);
    final var repository = createRepository(client, config);
    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SearchResponse.class)));

    // when
    repository.getProcessInstancesNextBatch();

    // then
    final var captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(client).search(captor.capture(), eq(ProcessInstanceForListViewEntity.class));
    final var query = captor.getValue().query();
    assertThat(query.isBool()).isTrue();
  }

  @Test
  public void shouldMapBatchForPIMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setRetentionMode(RetentionMode.PI);
    final var client = mock(OpenSearchAsyncClient.class);
    final var repository = createRepository(client, config);
    final var hit1 = mockHit("1", "2024-01-01", null);
    final var hit2 = mockHit("2", "2024-01-01", 100L);

    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse(List.of(hit1, hit2))));

    // when
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then
    assertThat(batch.ids()).containsKey(ListViewTemplate.PROCESS_INSTANCE_KEY);
    assertThat(batch.ids().get(ListViewTemplate.PROCESS_INSTANCE_KEY)).containsExactly("1", "2");
    assertThat(batch.ids()).doesNotContainKey(ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY);
  }

  @Test
  public void shouldMapBatchForPIHierarchyMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setRetentionMode(RetentionMode.PI_HIERARCHY);
    final var client = mock(OpenSearchAsyncClient.class);
    final var repository = createRepository(client, config);
    final var hit1 = mockHit("1", "2024-01-01", null); // Legacy
    final var hit2 = mockHit("2", "2024-01-01", 100L); // Root

    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse(List.of(hit1, hit2))));

    // when
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then
    assertThat(batch.ids())
        .containsKeys(
            ListViewTemplate.PROCESS_INSTANCE_KEY, ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY);
    assertThat(batch.ids().get(ListViewTemplate.PROCESS_INSTANCE_KEY)).containsExactly("1");
    // For root, it maps the root key, not the ID. hit2 has rootKey 100L.
    assertThat(batch.ids().get(ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY)).containsExactly("100");
  }

  @Test
  public void shouldMapBatchForPIHierarchyIgnoreLegacyMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setRetentionMode(RetentionMode.PI_HIERARCHY_IGNORE_LEGACY);
    final var client = mock(OpenSearchAsyncClient.class);
    final var repository = createRepository(client, config);

    final var hit1 = mockHit("1", "2024-01-01", null);
    final var hit2 = mockHit("2", "2024-01-01", 100L);

    when(client.search(any(SearchRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse(List.of(hit1, hit2))));

    // when
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then - purely based on mapping logic which splits by root key presence
    assertThat(batch.ids())
        .containsKeys(
            ListViewTemplate.PROCESS_INSTANCE_KEY, ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY);
    assertThat(batch.ids().get(ListViewTemplate.PROCESS_INSTANCE_KEY)).containsExactly("1");
    assertThat(batch.ids().get(ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY)).containsExactly("100");
  }

  private OpenSearchArchiverRepository createRepository(
      final OpenSearchAsyncClient client, final HistoryConfiguration config) {
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

  private Hit<ProcessInstanceForListViewEntity> mockHit(
      final String id, final String endDate, final Long rootPIKey) {
    final var hit = mock(Hit.class);
    when(hit.id()).thenReturn(id);

    // Mock the fields for endDate extraction
    final var jsonData = mock(JsonData.class);
    final var jsonArray = mock(jakarta.json.JsonArray.class);
    when(jsonData.toJson()).thenReturn(jsonArray);
    when(jsonArray.asJsonArray()).thenReturn(jsonArray);
    when(jsonArray.getString(0)).thenReturn(endDate);

    when(hit.fields()).thenReturn(Map.of(ListViewTemplate.END_DATE, jsonData));

    // Mock source
    final var entity = new ProcessInstanceForListViewEntity();
    entity.setRootProcessInstanceKey(rootPIKey);
    when(hit.source()).thenReturn(entity);

    return hit;
  }

  private SearchResponse<ProcessInstanceForListViewEntity> mockResponse(
      final List<Hit<ProcessInstanceForListViewEntity>> hits) {
    final var response = mock(SearchResponse.class);
    final var hitsMetadata = mock(HitsMetadata.class);
    when(response.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(hits);
    return response;
  }
}
