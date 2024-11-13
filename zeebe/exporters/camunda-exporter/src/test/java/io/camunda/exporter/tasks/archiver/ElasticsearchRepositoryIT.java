/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.indices.IndexSettingsLifecycle;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ExporterConfiguration.ArchiverConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.RetentionConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        List.of(new TestDocument("1"), new TestDocument("2"), new TestDocument("3"));
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
  void shouldReindexDocuments() throws IOException {
    // given
    final var sourceIndexName = UUID.randomUUID().toString();
    final var destIndexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(new TestDocument("1"), new TestDocument("2"), new TestDocument("3"));
    documents.forEach(doc -> index(sourceIndexName, doc));
    testClient.indices().refresh(r -> r.index(sourceIndexName));
    testClient.indices().create(r -> r.index(destIndexName));

    // when - reindex the first two documents
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

  @Test
  void shouldMoveDocuments() throws IOException {
    // given
    final var sourceIndexName = UUID.randomUUID().toString();
    final var destIndexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(new TestDocument("1"), new TestDocument("2"), new TestDocument("3"));
    documents.forEach(doc -> index(sourceIndexName, doc));
    testClient.indices().refresh(r -> r.index(sourceIndexName));
    testClient.indices().create(r -> r.index(destIndexName));

    // when - move the first two documents
    final var result =
        repository.moveDocuments(
            sourceIndexName,
            destIndexName,
            "id",
            documents.stream().limit(2).map(TestDocument::id).toList(),
            Runnable::run);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(sourceIndexName, destIndexName));
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
        .as("only the last document is remaining")
        .hasSize(1)
        .extracting(Hit::id)
        .containsExactlyInAnyOrder("3");
  }

  @Test
  void shouldGetProcessInstancesNextBatch() throws IOException {
    // given - 4 documents, where 2 is on a different partition, 3 is the wrong join relation type,
    // and 4 was finished too recently: we then expect only 1 to be returned
    final var now = Instant.now();
    final var twoHoursAgo = now.minus(Duration.ofHours(2)).toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestProcessInstance(
                "1", twoHoursAgo, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 1),
            new TestProcessInstance(
                "2", twoHoursAgo, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 2),
            new TestProcessInstance("3", twoHoursAgo, ListViewTemplate.ACTIVITIES_JOIN_RELATION, 1),
            new TestProcessInstance(
                "4", now.toString(), ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 1));

    // create the index template first to ensure ID is a keyword, otherwise the surrounding
    // aggregation will fail
    createProcessInstanceIndex();
    documents.forEach(doc -> index(processInstanceIndex, doc));
    testClient.indices().refresh(r -> r.index(processInstanceIndex));
    config.setRolloverBatchSize(3);

    // when
    final var result = repository.getProcessInstancesNextBatch();

    // then - we expect only the first document created two hours ago to be returned
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.ids()).containsExactly("1");
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
  }

  @Test
  void shouldGetBatchOperationsNextBatch() throws IOException {
    // given - 3 documents, two of which were created over an hour ago, one of which was created
    final var now = Instant.now();
    final var twoHoursAgo = now.minus(Duration.ofHours(2)).toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestBatchOperation("1", twoHoursAgo),
            new TestBatchOperation("2", twoHoursAgo),
            new TestBatchOperation("3", now.toString()));

    // create the index template first to ensure ID is a keyword, otherwise the surrounding
    // aggregation will fail
    createBatchOperationIndex();
    documents.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));
    config.setRolloverBatchSize(3);

    // when
    final var result = repository.getBatchOperationsNextBatch();

    // then - we expect only the first two documents created two hours ago to be returned
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.ids()).containsExactly("1", "2");
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
  }

  private <T extends TDocument> void index(final String index, final T document) {
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

  private void createProcessInstanceIndex() throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var endDateProp =
        Property.of(p -> p.date(d -> d.index(true).format("date_time || epoch_millis")));
    final var joinRelationProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        ListViewTemplate.ID,
                        idProp,
                        ListViewTemplate.END_DATE,
                        endDateProp,
                        ListViewTemplate.JOIN_RELATION,
                        joinRelationProp)));
    testClient.indices().create(r -> r.index(processInstanceIndex).mappings(properties));
  }

  private void createBatchOperationIndex() throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var endDateProp =
        Property.of(p -> p.date(d -> d.index(true).format("date_time || epoch_millis")));
    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        BatchOperationTemplate.ID,
                        idProp,
                        BatchOperationTemplate.END_DATE,
                        endDateProp)));
    testClient.indices().create(r -> r.index(batchOperationIndex).mappings(properties));
  }

  private record TestDocument(String id) implements TDocument {}

  private record TestBatchOperation(String id, String endDate) implements TDocument {}

  private record TestProcessInstance(
      String id, String endDate, String joinRelation, int partitionId) implements TDocument {}

  private interface TDocument {
    String id();
  }
}
