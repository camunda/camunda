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
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.PendingIncidentUpdateBatch;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
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
final class ElasticsearchIncidentUpdateRepositoryIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final int PARTITION_ID = 1;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  @AutoCloseResource private final RestClientTransport transport = createRestClient();
  private final String indexPrefix = UUID.randomUUID().toString();
  private final ElasticsearchClient testClient = new ElasticsearchClient(transport);
  private final ElasticsearchEngineClient searchClient = new ElasticsearchEngineClient(testClient);
  private final ElasticsearchAsyncClient client = new ElasticsearchAsyncClient(transport);
  private final PostImporterQueueTemplate postImporterQueueTemplate =
      new PostImporterQueueTemplate(indexPrefix, true);
  private final IncidentTemplate incidentTemplate = new IncidentTemplate(indexPrefix, true);
  private final ListViewTemplate listViewTemplate = new ListViewTemplate(indexPrefix, true);
  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate =
      new FlowNodeInstanceTemplate(indexPrefix, true);

  @BeforeEach
  void beforeEach() {
    Stream.of(
            postImporterQueueTemplate, incidentTemplate, listViewTemplate, flowNodeInstanceTemplate)
        .forEach(template -> searchClient.createIndexTemplate(template, new IndexSettings(), true));
  }

  private ElasticsearchIncidentUpdateRepository createRepository() {
    return new ElasticsearchIncidentUpdateRepository(
        PARTITION_ID,
        postImporterQueueTemplate.getAlias(),
        incidentTemplate.getAlias(),
        listViewTemplate.getAlias(),
        flowNodeInstanceTemplate.getAlias(),
        client,
        Runnable::run);
  }

  private RestClientTransport createRestClient() {
    final var restClient =
        RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
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

  @Nested
  final class GetPendingIncidentsBatchTest {
    @Test
    void shouldGetOnlyNewUpdatesByPosition() throws IOException {
      // given
      final var repository = createRepository();
      final var updates = setupIncidentUpdates(1, 2);
      testClient.bulk(b -> b.operations(updates).refresh(Refresh.True));

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
    void shouldKeepOnlyLatestUpdatePerKey() throws IOException {
      // given
      final var repository = createRepository();
      final var updates =
          setupIncidentUpdates(
              1,
              2L,
              e ->
                  e.setKey(1L)
                      .setIntent(
                          e.getPosition() == 2
                              ? IncidentIntent.RESOLVED.name()
                              : IncidentIntent.CREATED.name()));
      testClient.bulk(b -> b.operations(updates).refresh(Refresh.True));

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
    void shouldGetUpdates() throws IOException {
      // given - incident 1 is resolved, incident 2 is created
      final var repository = createRepository();
      final var updates =
          setupIncidentUpdates(
              1,
              2,
              e ->
                  e.setIntent(
                      e.getKey() == 1
                          ? IncidentIntent.RESOLVED.name()
                          : IncidentIntent.CREATED.name()));
      testClient.bulk(b -> b.operations(updates).refresh(Refresh.True));

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
    void shouldGetByPartitionId() throws IOException {
      // given - incident 1 on partition 1, incident 2 on partition 2
      final var repository = createRepository();
      final var updates =
          setupIncidentUpdates(1, 2, e -> e.setPartitionId(Math.toIntExact(e.getKey())));
      testClient.bulk(b -> b.operations(updates).refresh(Refresh.True));

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

    private List<BulkOperation> setupIncidentUpdates(
        final long fromPosition, final long toPosition) {
      return setupIncidentUpdates(fromPosition, toPosition, ignored -> {});
    }

    private List<BulkOperation> setupIncidentUpdates(
        final long fromPosition,
        final long toPosition,
        final Consumer<PostImporterQueueEntity> modifier) {
      return LongStream.rangeClosed(fromPosition, toPosition)
          .mapToObj(position -> newPendingUpdate().setPosition(position).setKey(position))
          .peek(modifier)
          .map(
              doc ->
                  IndexOperation.of(
                      i -> i.index(postImporterQueueTemplate.getFullQualifiedName()).document(doc)))
          .map(op -> BulkOperation.of(b -> b.index(op)))
          .toList();
    }
  }
}
