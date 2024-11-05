/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.indices.IndexSettingsLifecycle;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.ArchiverConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.RetentionConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("resource")
@Testcontainers
@AutoCloseResources
final class ElasticsearchRepositoryIT {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchRepositoryIT.class);

  @Container
  private static final ElasticsearchContainer ELASTIC =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @AutoCloseResource private final RestClientTransport transport = createRestClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ArchiverConfiguration config = new ArchiverConfiguration();
  private final RetentionConfiguration retention = new RetentionConfiguration();
  private final String processInstanceIndex = "process-instance-" + UUID.randomUUID();
  private final String batchOperationIndex = "batch-operation-" + UUID.randomUUID();
  private final ElasticsearchClient testClient = new ElasticsearchClient(transport);

  @Test
  void shouldDeleteDocuments() throws IOException {
    // given
    final var indexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestDocument("1", "2024-01-01"),
            new TestDocument("2", "2024-01-04"),
            new TestDocument("3", "2024-01-02"));
    documents.forEach(doc -> index(indexName, doc));
    testClient.indices().refresh(r -> r.index(indexName));

    // when - delete the first two documents
    final var result =
        repository.deleteDocuments(
            indexName, "id", documents.stream().limit(2).map(TestDocument::id).toList());

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(indexName));
    final var remaining =
        testClient.search(r -> r.index(indexName).requestCache(false), TestDocument.class);
    assertThat(remaining.hits().hits())
        .as("only the third document is remaining")
        .hasSize(1)
        .first()
        .extracting(Hit::id)
        .isEqualTo("3");
  }

  @Test
  void shouldSetIndexLifeCycle() throws IOException {
    // given
    final var indexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    testClient.indices().create(r -> r.index(indexName));
    final var initialLifecycle =
        testClient
            .indices()
            .getSettings(r -> r.index(indexName))
            .get(indexName)
            .settings()
            .lifecycle();
    assertThat(initialLifecycle).isNull();
    retention.setEnabled(true);
    retention.setPolicyName("operate_delete_archived_indices");
    putLifecyclePolicy();

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var actualLifecycle =
        testClient
            .indices()
            .getSettings(r -> r.index(indexName))
            .get(indexName)
            .settings()
            .index()
            .lifecycle();
    assertThat(actualLifecycle)
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("operate_delete_archived_indices");
  }

  @Test
  void shouldNotSetIndexLifecycleIfRetentionIsDisabled() throws IOException {
    // given
    final var indexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    testClient.indices().create(r -> r.index(indexName));
    final var initialLifecycle =
        testClient
            .indices()
            .getSettings(r -> r.index(indexName))
            .get(indexName)
            .settings()
            .lifecycle();
    assertThat(initialLifecycle).isNull();
    retention.setEnabled(false);
    retention.setPolicyName("operate_delete_archived_indices");
    putLifecyclePolicy();

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ZERO);
    final var actualLifecycle =
        testClient
            .indices()
            .getSettings(r -> r.index(indexName))
            .get(indexName)
            .settings()
            .index()
            .lifecycle();
    assertThat(actualLifecycle).isNull();
  }

  @Test
  void shouldReindexDocuments() throws IOException {
    // given
    final var sourceIndexName = UUID.randomUUID().toString();
    final var destIndexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestDocument("1", "2024-01-01"),
            new TestDocument("2", "2024-01-04"),
            new TestDocument("3", "2024-01-02"));
    documents.forEach(doc -> index(sourceIndexName, doc));
    testClient.indices().refresh(r -> r.index(sourceIndexName));
    testClient.indices().create(r -> r.index(destIndexName));

    // when - delete the first two documents
    final var result =
        repository.reindexDocuments(
            sourceIndexName,
            destIndexName,
            "id",
            documents.stream().limit(2).map(TestDocument::id).toList());

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(destIndexName));
    final var remaining =
        testClient.search(r -> r.index(sourceIndexName).requestCache(false), TestDocument.class);
    final var reindexed =
        testClient.search(r -> r.index(destIndexName).requestCache(false), TestDocument.class);
    assertThat(reindexed.hits().hits())
        .as("only first two documents were reindexed")
        .hasSize(2)
        .map(Hit::id)
        .containsExactlyInAnyOrder("1", "2");
    assertThat(remaining.hits().hits())
        .as("all documents are remaining")
        .hasSize(3)
        .extracting(Hit::id)
        .containsExactlyInAnyOrder("1", "2", "3");
  }

  private void index(final String index, final TestDocument document) {
    try {
      testClient.index(b -> b.index(index).document(document).id(document.id()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void putLifecyclePolicy() throws IOException {
    final var ilmClient = testClient.ilm();
    final var phase =
        Phase.of(
            d -> d.minAge(t -> t.time("30d")).actions(JsonData.of(Map.of("delete", Map.of()))));
    ilmClient.putLifecycle(
        l -> l.name(retention.getPolicyName()).policy(p -> p.phases(h -> h.delete(phase))));
  }

  // no need to close resource returned here, since the transport is closed above anyway
  private ElasticsearchRepository createRepository() {
    final var client = new ElasticsearchAsyncClient(transport);
    final var metrics = new CamundaExporterMetrics(meterRegistry);

    return new ElasticsearchRepository(
        1,
        config,
        retention,
        processInstanceIndex,
        batchOperationIndex,
        client,
        Runnable::run,
        metrics,
        LOGGER);
  }

  private RestClientTransport createRestClient() {
    final var restClient =
        RestClient.builder(HttpHost.create(ELASTIC.getHttpHostAddress())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }

  /**
   * NOTE: the fields of this class have to match the ID and END_DATE fields in {@link
   * io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate} and {@link
   * io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate}
   *
   * <p>If those change in the future, please keep them up to date here. If you need to split the
   * document types as the names diverge, then go ahead and do so.
   */
  private record TestDocument(String id, String endDate) {}
}
