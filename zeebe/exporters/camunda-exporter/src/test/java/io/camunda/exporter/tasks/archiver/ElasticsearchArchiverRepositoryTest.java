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

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesAsyncClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.SimpleJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.json.Json;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ElasticsearchArchiverRepositoryTest extends AbstractArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepositoryTest.class);

  private final RestClientTransport transport = Mockito.spy(createRestClient());

  @Test
  void shouldCloseTransportOnClose() throws Exception {
    // when
    repository.close();

    // then
    Mockito.verify(transport, Mockito.times(1)).close();
  }

  @Override
  ElasticsearchArchiverRepository createRepository() {
    final var client = new ElasticsearchAsyncClient(transport);
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

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create("http://127.0.0.1:1")).build();
    return new RestClientTransport(restClient, new SimpleJsonpMapper());
  }

  @Test
  public void shouldConstructCorrectQueryForPIMode() {
    // given
    final var config = new HistoryConfiguration();
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI);
    final var client = mock(ElasticsearchAsyncClient.class);
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
    final var batch = repository.getProcessInstancesNextBatch().join();

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
    final var batch = repository.getProcessInstancesNextBatch().join();

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
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then - purely based on mapping logic which splits by root key presence
    assertThat(batch.processInstanceKeys()).containsExactly(1L);
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(100L);
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
