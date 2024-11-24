/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.PendingIncidentUpdateBatch;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.operate.post.PostImporterQueueEntity;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoCloseResources
abstract sealed class IncidentUpdateRepositoryIT {
  private static final int PARTITION_ID = 1;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final String indexPrefix = UUID.randomUUID().toString();
  protected final PostImporterQueueTemplate postImporterQueueTemplate =
      new PostImporterQueueTemplate(indexPrefix, true);
  protected final IncidentTemplate incidentTemplate = new IncidentTemplate(indexPrefix, true);
  protected final ListViewTemplate listViewTemplate = new ListViewTemplate(indexPrefix, true);
  protected final FlowNodeInstanceTemplate flowNodeInstanceTemplate =
      new FlowNodeInstanceTemplate(indexPrefix, true);

  @AutoCloseResource private final ClientAdapter clientAdapter;
  private final SearchEngineClient engineClient;

  protected IncidentUpdateRepositoryIT(final String databaseUrl) {
    final var config = new ExporterConfiguration();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(databaseUrl);

    clientAdapter = ClientAdapter.of(config);
    engineClient = clientAdapter.getSearchEngineClient();
  }

  @BeforeEach
  void beforeEach() {
    Stream.of(
            postImporterQueueTemplate, incidentTemplate, listViewTemplate, flowNodeInstanceTemplate)
        .forEach(template -> engineClient.createIndexTemplate(template, new IndexSettings(), true));
  }

  protected abstract IncidentUpdateRepository createRepository();

  static final class ElasticsearchIT extends IncidentUpdateRepositoryIT {
    @Container
    private static final ElasticsearchContainer CONTAINER =
        TestSearchContainers.createDefeaultElasticsearchContainer();

    @AutoCloseResource private final RestClientTransport transport = createTransport();

    public ElasticsearchIT() {
      super("http://" + CONTAINER.getHttpHostAddress());
    }

    @Override
    protected IncidentUpdateRepository createRepository() {
      return new ElasticsearchIncidentUpdateRepository(
          PARTITION_ID,
          postImporterQueueTemplate.getAlias(),
          incidentTemplate.getAlias(),
          listViewTemplate.getAlias(),
          flowNodeInstanceTemplate.getAlias(),
          new ElasticsearchAsyncClient(transport),
          Runnable::run);
    }

    private RestClientTransport createTransport() {
      final var restClient =
          RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();
      return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }
  }

  @Nested
  final class GetIncidentDocumentsTest {
    @Test
    void shouldReturnEmptyMap() {
      // given
      final var repository = createRepository();

      // when
      final var documents = repository.getIncidentDocuments(List.of("1"));

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.map(Long.class, IncidentDocument.class))
          .isEmpty();
    }

    @Test
    void shouldReturnIncidentByIds() throws IOException, PersistenceException {
      // given
      final var repository = createRepository();
      final var expected = createIncident(1L);
      createIncident(2L);

      // when
      final var documents = repository.getIncidentDocuments(List.of("1"));

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.map(String.class, IncidentDocument.class))
          .hasSize(1)
          .containsEntry(
              "1", new IncidentDocument("1", incidentTemplate.getFullQualifiedName(), expected));
    }

    private IncidentEntity createIncident(final long key) throws IOException, PersistenceException {
      final var incident = newIncident(key);

      indexIncident(incident);
      return incident;
    }

    private void indexIncident(final IncidentEntity incident) throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(incidentTemplate.getFullQualifiedName(), incident);
      batchRequest.executeWithRefresh();
    }

    private IncidentEntity newIncident(final long key) {
      final var incident = new IncidentEntity();
      final var id = String.valueOf(key);

      incident.setState(IncidentState.PENDING);
      incident.setId(id);
      incident.setKey(key);
      incident.setProcessInstanceKey(key);
      incident.setFlowNodeInstanceKey(key);
      incident.setFlowNodeId(id);
      incident.setPartitionId(PARTITION_ID);
      incident.setErrorMessage("failure");

      return incident;
    }
  }

  @Nested
  final class GetPendingIncidentsBatchTest {
    @Test
    void shouldGetOnlyNewUpdatesByPosition() throws PersistenceException {
      // given
      final var repository = createRepository();
      setupIncidentUpdates(1, 2);

      // when
      final var batch = repository.getPendingIncidentsBatch(1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(
              PendingIncidentUpdateBatch::highestPosition,
              PendingIncidentUpdateBatch::newIncidentStates)
          .containsExactly(2L, Map.of(2L, IncidentState.ACTIVE));
    }

    @Test
    void shouldKeepOnlyLatestUpdatePerKey() throws PersistenceException {
      // given
      final var repository = createRepository();
      setupIncidentUpdates(
          1,
          2L,
          e ->
              e.setKey(1L)
                  .setIntent(
                      e.getPosition() == 2
                          ? IncidentIntent.RESOLVED.name()
                          : IncidentIntent.CREATED.name()));

      // when
      final var batch = repository.getPendingIncidentsBatch(1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .returns(2L, PendingIncidentUpdateBatch::highestPosition)
          .extracting(PendingIncidentUpdateBatch::newIncidentStates)
          .asInstanceOf(InstanceOfAssertFactories.map(Long.class, IncidentState.class))
          .hasSize(1)
          .containsEntry(1L, IncidentState.RESOLVED);
    }

    @Test
    void shouldGetUpdates() throws PersistenceException {
      // given - incident 1 is resolved, incident 2 is created
      final var repository = createRepository();
      setupIncidentUpdates(
          1,
          2,
          e ->
              e.setIntent(
                  e.getKey() == 1
                      ? IncidentIntent.RESOLVED.name()
                      : IncidentIntent.CREATED.name()));

      // when
      final var batch = repository.getPendingIncidentsBatch(-1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(PendingIncidentUpdateBatch::newIncidentStates)
          .asInstanceOf(InstanceOfAssertFactories.map(Long.class, IncidentState.class))
          .hasSize(2)
          .containsEntry(1L, IncidentState.RESOLVED)
          .containsEntry(2L, IncidentState.ACTIVE);
    }

    @Test
    void shouldGetByPartitionId() throws PersistenceException {
      // given - incident 1 on partition 1, incident 2 on partition 2
      final var repository = createRepository();
      setupIncidentUpdates(1, 2, e -> e.setPartitionId(Math.toIntExact(e.getKey())));

      // when
      final var batch = repository.getPendingIncidentsBatch(-1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(
              PendingIncidentUpdateBatch::highestPosition,
              PendingIncidentUpdateBatch::newIncidentStates)
          .containsExactly(1L, Map.of(1L, IncidentState.ACTIVE));
    }

    @Test
    void shouldReturnEmptyBatch() {
      // given
      final var repository = createRepository();

      // when
      final var batch = repository.getPendingIncidentsBatch(-1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(
              PendingIncidentUpdateBatch::highestPosition,
              PendingIncidentUpdateBatch::newIncidentStates)
          .containsExactly(-1L, Collections.emptyMap());
    }

    private PostImporterQueueEntity newPendingUpdate() {
      return new PostImporterQueueEntity()
          .setActionType(PostImporterActionType.INCIDENT)
          .setIntent(IncidentIntent.CREATED.name())
          .setKey(1L)
          .setPartitionId(PARTITION_ID)
          .setProcessInstanceKey(1L)
          .setPosition(1L);
    }

    private void setupIncidentUpdates(final long fromPosition, final long toPosition)
        throws PersistenceException {
      setupIncidentUpdates(fromPosition, toPosition, ignored -> {});
    }

    private void setupIncidentUpdates(
        final long fromPosition,
        final long toPosition,
        final Consumer<PostImporterQueueEntity> modifier)
        throws PersistenceException {
      final var updates =
          LongStream.rangeClosed(fromPosition, toPosition)
              .mapToObj(position -> newPendingUpdate().setPosition(position).setKey(position))
              .peek(modifier)
              .toList();
      final var batchRequest = clientAdapter.createBatchRequest();
      updates.forEach(e -> batchRequest.add(postImporterQueueTemplate.getFullQualifiedName(), e));
      batchRequest.executeWithRefresh();
    }
  }
}
