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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.search.schema.config.IndexRetentionPolicy;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("resource")
@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
final class ElasticsearchArchiverRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepositoryIT.class);

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @AutoClose private final RestClientTransport transport = createRestClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoryConfiguration config = new HistoryConfiguration();
  private final RetentionConfiguration retention = new RetentionConfiguration();
  private String indexPrefix = "test-prefix";
  private final String zeebeIndexPrefix = "zeebe-record";
  private final String processInstanceIndex = "process-instance-" + UUID.randomUUID();
  private final String batchOperationIndex = "batch-operation-" + UUID.randomUUID();
  private final String zeebeIndex = zeebeIndexPrefix + "-" + UUID.randomUUID();
  private final ElasticsearchClient testClient = new ElasticsearchClient(transport);

  @AfterEach
  void afterEach() throws IOException {
    // wipes all data in ES between tests
    final var response = transport.restClient().performRequest(new Request("DELETE", "_all"));
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
  }

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
    final var initialLifecycle = getLifeCycle(indexName);
    assertThat(initialLifecycle).isNull();
    retention.setEnabled(true);
    retention.setPolicyName("operate_delete_archived_indices");
    putLifecyclePolicy();

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var actualLifecycle = getLifeCycle(indexName);
    assertThat(actualLifecycle)
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("operate_delete_archived_indices");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  void shouldSetIndexLifeCycleOnAllValidIndexes(final String prefix) throws IOException {
    // given
    indexPrefix = prefix;
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(prefix);
    final var expectedIndices =
        List.of(
            formattedPrefix + "operate-record-8.2.1_2024-01-02",
            formattedPrefix + "tasklist-record-8.3.0_2024-01");
    final var untouchedIndices =
        new ArrayList<>(
            List.of(
                formattedPrefix + "operate-record-8.2.1_", "other-" + "tasklist-record-8.3.0_"));

    // we cannot test the case with multiple different prefixes when no prefix is given, since it
    // will just match everything from the other prefixes...
    if (!prefix.isEmpty()) {
      untouchedIndices.add("other-" + "tasklist-record-8.3.0_2024-01-02");
    }

    final var repository = createRepository();
    final var indices = new ArrayList<>(expectedIndices);
    indices.addAll(untouchedIndices);

    retention.setEnabled(true);
    retention.setPolicyName("operate_delete_archived_indices");

    putLifecyclePolicy();
    for (final var index : indices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    for (final var index : expectedIndices) {
      assertThat(getLifeCycle(index))
          .isNotNull()
          .extracting(IndexSettingsLifecycle::name)
          .isEqualTo("operate_delete_archived_indices");
    }
    for (final var index : untouchedIndices) {
      assertThat(getLifeCycle(index)).as("no policy applied to %s", index).isNull();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  void shouldApplyCustomIndexPoliciesWithExactMatching(final String prefix) throws IOException {
    // given
    indexPrefix = prefix;
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var expectedIndices =
        List.of(
            formattedPrefix + "camunda-user-task-8.8.0_2025-08-10",
            formattedPrefix + "camunda-user-task-at-8.8.0_2025-08-10",
            formattedPrefix + "operate-list-view-8.3.0_2024-01-02",
            formattedPrefix + "tasklist-task-8.5.0_2024-01-02");

    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_policy");

    // Configure exact index name matches

    retention.setIndexPolicies(
        Map.of(
            "camunda-user-task", new IndexRetentionPolicy("user_task_policy", "7d"),
            "camunda-user-task-at", new IndexRetentionPolicy("user_task_at_policy", "14d")));

    putLifecyclePolicies();
    for (final var index : expectedIndices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Verify exact matches get their specific policies
    assertThat(getLifeCycle(formattedPrefix + "camunda-user-task-8.8.0_2025-08-10").name())
        .isEqualTo("user_task_policy");
    assertThat(getLifeCycle(formattedPrefix + "camunda-user-task-at-8.8.0_2025-08-10").name())
        .isEqualTo("user_task_at_policy");

    // Verify indices without custom policies get default policy
    assertThat(getLifeCycle(formattedPrefix + "operate-list-view-8.3.0_2024-01-02").name())
        .isEqualTo("default_policy");
    assertThat(getLifeCycle(formattedPrefix + "tasklist-task-8.5.0_2024-01-02").name())
        .isEqualTo("default_policy");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  void shouldApplyCustomIndexPoliciesWithPatternMatching(final String prefix) throws IOException {
    // given
    indexPrefix = prefix;
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var expectedIndices =
        List.of(
            formattedPrefix + "camunda-user-task-8.8.0_2025-08-10",
            formattedPrefix + "camunda-user-task-at-8.8.0_2025-08-10",
            formattedPrefix + "camunda-authorization-8.8.0_2025-08-10",
            formattedPrefix + "operate-list-view-8.3.0_2024-01-02",
            formattedPrefix + "operate-process-8.3.0_2024-01-02",
            formattedPrefix + "tasklist-task-8.5.0_2024-01-02",
            formattedPrefix + "other-index-8.8.0_2025-08-10");

    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_policy");

    // Configure pattern-based policies
    retention.setIndexPolicies(
        Map.of(
            "camunda.*", new IndexRetentionPolicy("camunda_policy", "40d"),
            "operate.*", new IndexRetentionPolicy("operate_policy", "60d"),
            "tasklist.*", new IndexRetentionPolicy("tasklist_policy", "90d")));

    putLifecyclePolicies();
    for (final var index : expectedIndices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Verify pattern matches get their specific policies
    assertThat(getLifeCycle(formattedPrefix + "camunda-user-task-8.8.0_2025-08-10").name())
        .isEqualTo("camunda_policy");
    assertThat(getLifeCycle(formattedPrefix + "camunda-user-task-at-8.8.0_2025-08-10").name())
        .isEqualTo("camunda_policy");
    assertThat(getLifeCycle(formattedPrefix + "camunda-authorization-8.8.0_2025-08-10").name())
        .isEqualTo("camunda_policy");

    assertThat(getLifeCycle(formattedPrefix + "operate-list-view-8.3.0_2024-01-02").name())
        .isEqualTo("operate_policy");
    assertThat(getLifeCycle(formattedPrefix + "operate-process-8.3.0_2024-01-02").name())
        .isEqualTo("operate_policy");

    assertThat(getLifeCycle(formattedPrefix + "tasklist-task-8.5.0_2024-01-02").name())
        .isEqualTo("tasklist_policy");

    // Verify indices without matching patterns get default policy
    assertThat(getLifeCycle(formattedPrefix + "other-index-8.8.0_2025-08-10").name())
        .isEqualTo("default_policy");
  }

  @Test
  void shouldHandleEmptyIndexPoliciesGracefully() throws IOException {
    // given
    indexPrefix = "empty";
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var testIndices =
        List.of(
            formattedPrefix + "test-index-1-8.8.0_2025-08-10",
            formattedPrefix + "test-index-2-8.8.0_2025-08-10");

    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_only_policy");
    // Explicitly set empty index policies
    retention.setIndexPolicies(Map.of());

    putLifecyclePolicies();
    for (final var index : testIndices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Verify all indices get the default policy when no index policies are configured
    testIndices.forEach(
        index ->
            assertThat(getLifeCycle(index).name())
                .as(
                    "Index %s should have default_only_policy when no index policies are configured",
                    index)
                .isEqualTo("default_only_policy"));
  }

  @Test
  void shouldNotFailSettingILMOnMissingIndex() throws IOException {
    // given
    final var repository = createRepository();
    final var indexName = UUID.randomUUID().toString();
    retention.setEnabled(true);
    retention.setPolicyName("operate_delete_archived_indices");
    putLifecyclePolicy();

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
  }

  @Test
  void shouldApplyDifferentPoliciesForDifferentIndices() throws IOException {
    // given
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var userTaskIndex = formattedPrefix + "camunda-user-task-8.8.0_2025-08-10";
    final var operateIndex = formattedPrefix + "operate-list-view-8.3.0_2024-01-02";
    final var tasklistIndex = formattedPrefix + "tasklist-task-8.5.0_2024-01-02";
    final var repository = createRepository();

    testClient.indices().create(r -> r.index(userTaskIndex));
    testClient.indices().create(r -> r.index(operateIndex));
    testClient.indices().create(r -> r.index(tasklistIndex));

    retention.setEnabled(true);
    retention.setPolicyName("default_policy");
    retention.setIndexPolicies(
        Map.of(
            "camunda-user-task", new IndexRetentionPolicy("user_task_policy", "7d"),
            "operate.*", new IndexRetentionPolicy("operate_policy", "30d"),
            "tasklist.*", new IndexRetentionPolicy("tasklist_policy", "90d")));

    putLifecyclePolicies();

    // when
    final var result = repository.setIndexLifeCycle(userTaskIndex, operateIndex, tasklistIndex);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    assertThat(getLifeCycle(userTaskIndex))
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("user_task_policy");

    assertThat(getLifeCycle(operateIndex))
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("operate_policy");

    assertThat(getLifeCycle(tasklistIndex))
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("tasklist_policy");
  }

  @Test
  void shouldApplyDefaultPolicyWhenNoCustomPolicyMatches() throws IOException {
    // given
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var indexName = formattedPrefix + "some-other-index-8.8.0_2025-08-10";
    final var repository = createRepository();
    testClient.indices().create(r -> r.index(indexName));

    retention.setEnabled(true);
    retention.setPolicyName("default_policy");
    retention.setIndexPolicies(
        Map.of(
            "camunda-user-task", new IndexRetentionPolicy("custom_policy", "14d"),
            "operate.*", new IndexRetentionPolicy("operate_policy", "60d")));

    putLifecyclePolicies();

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var actualLifecycle = getLifeCycle(indexName);
    assertThat(actualLifecycle)
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("default_policy");
  }

  @Test
  void shouldReturnCompletedFutureWhenRetentionDisabled() throws IOException {
    // given
    final var indexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    testClient.indices().create(r -> r.index(indexName));

    retention.setEnabled(false); // Retention disabled
    retention.setPolicyName("some_policy");

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    final var actualLifecycle = getLifeCycle(indexName);
    assertThat(actualLifecycle).isNull(); // No policy should be applied
  }

  private IndexSettingsLifecycle getLifeCycle(final String indexName) {
    try {
      return testClient
          .indices()
          .getSettings(r -> r.index(indexName))
          .get(indexName)
          .settings()
          .index()
          .lifecycle();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  @Test
  void shouldSetTheCorrectFinishDateWithRollover() throws IOException {
    // given a rollover of 3 days:
    config.setRolloverInterval("3d");
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    final var destIndexName = UUID.randomUUID().toString();
    final var now = Instant.now();
    final var fourDaysAgo = now.minus(Duration.ofDays(4)).toString();
    final var twoDaysAgo = now.minus(Duration.ofDays(2)).toString();
    final var twoHoursAgo = now.minus(Duration.ofHours(2)).toString();
    final var repository = createRepository();
    final var documents =
        List.of(new TestBatchOperation("1", fourDaysAgo), new TestBatchOperation("2", fourDaysAgo));

    createBatchOperationIndex();
    documents.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));

    // when
    final var firstBatch = repository.getBatchOperationsNextBatch();
    Awaitility.await("waiting for first batch operation to be complete")
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                firstBatch.isDone()
                    && firstBatch.get().ids().containsAll(List.of("1", "2"))
                    && firstBatch
                        .get()
                        .finishDate()
                        .equals(dateFormatter.format(now.minus(Duration.ofDays(4)))));
    repository
        .moveDocuments(batchOperationIndex, destIndexName, "id", List.of("1", "2"), Runnable::run)
        .join();

    final var secondBatchDocuments =
        List.of(new TestBatchOperation("3", twoDaysAgo), new TestBatchOperation("4", twoDaysAgo));

    secondBatchDocuments.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));

    // then
    final var secondBatch = repository.getBatchOperationsNextBatch();
    Awaitility.await("waiting for second batch operation to be complete")
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                secondBatch.isDone()
                    && secondBatch.get().ids().containsAll(List.of("3", "4"))
                    && secondBatch
                        .get()
                        .finishDate()
                        .equals(dateFormatter.format(now.minus(Duration.ofDays(4)))));
    // it should still have the same finish date since the rollover window is three days, and the
    // difference of both batches is only 2 days.
    repository
        .moveDocuments(batchOperationIndex, destIndexName, "id", List.of("3", "4"), Runnable::run)
        .join();

    // we create another batch of documents, which is two hours ago, since the default archive point
    // is after 1 hour
    final var thirdBatchDocuments =
        List.of(new TestBatchOperation("5", twoHoursAgo), new TestBatchOperation("6", twoHoursAgo));

    thirdBatchDocuments.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));

    // then
    final var thirdBatch = repository.getBatchOperationsNextBatch();

    Awaitility.await("waiting for third batch operation to be complete")
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                thirdBatch.isDone()
                    && thirdBatch.get().ids().containsAll(List.of("5", "6"))
                    && thirdBatch
                        .get()
                        .finishDate()
                        .equals(dateFormatter.format(now.minus(Duration.ofHours(2)))));
  }

  @Test
  void shouldFetchHistoricalDatesOnStart() throws IOException {
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    final var now = Instant.now();
    final var documents =
        List.of(
            new TestBatchOperation("1", now.minus(Duration.ofDays(1)).toString()),
            new TestBatchOperation("2", now.minus(Duration.ofDays(1)).toString()));

    final var repository = createRepository();
    // we have an already existing index with a date of 3 days ago.
    testClient
        .indices()
        .create(
            r ->
                r.index(
                    batchOperationIndex
                        + "_"
                        + dateFormatter.format(now.minus(Duration.ofDays(3)))));

    createBatchOperationIndex();
    documents.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));
    config.setRolloverInterval("3d");

    // then the batch finish date should not update:
    final var batch = repository.getBatchOperationsNextBatch().join();
    assertThat(batch.ids()).containsAll(List.of("1", "2"));
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofDays(3))));
  }

  @Test
  void shouldFetchHistoricalDatesOnStartAndExcludeZeebePrefix() throws IOException {
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    final var now = Instant.now();
    final var documents =
        List.of(
            new TestBatchOperation("1", now.minus(Duration.ofDays(1)).toString()),
            new TestBatchOperation("2", now.minus(Duration.ofDays(1)).toString()));

    final var repository = createRepository();
    // we have an already existing Zeebe index with a date of 3 days ago.
    testClient
        .indices()
        .create(
            r -> r.index(zeebeIndex + "_" + dateFormatter.format(now.minus(Duration.ofDays(3)))));

    createBatchOperationIndex();
    documents.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));
    config.setRolloverInterval("3d");

    // then the batch finish date should update since zeebe index should be excluded:
    final var batch = repository.getBatchOperationsNextBatch().join();
    assertThat(batch.ids()).containsAll(List.of("1", "2"));
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofDays(1))));
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
        Phase.of(d -> d.minAge(t -> t.time("30d")).actions(a -> a.delete(del -> del)));
    ilmClient.putLifecycle(
        l -> l.name(retention.getPolicyName()).policy(p -> p.phases(h -> h.delete(phase))));
  }

  private void putLifecyclePolicies() throws IOException {
    putLifecyclePolicy(retention.getPolicyName(), retention.getMinimumAge());

    for (var indexPolicy : retention.getIndexPolicies().values()) {
      putLifecyclePolicy(indexPolicy.getPolicyName(), indexPolicy.getMinimumAge());
    }
  }

  private void putLifecyclePolicy(final String policyName, final String minAge) throws IOException {
    final var ilmClient = testClient.ilm();
    final var phase =
        Phase.of(d -> d.minAge(t -> t.time(minAge)).actions(a -> a.delete(del -> del)));
    ilmClient.putLifecycle(l -> l.name(policyName).policy(p -> p.phases(h -> h.delete(phase))));
  }

  // no need to close resource returned here, since the transport is closed above anyway
  private ElasticsearchArchiverRepository createRepository() {
    final var client = new ElasticsearchAsyncClient(transport);
    final var metrics = new CamundaExporterMetrics(meterRegistry);

    return new ElasticsearchArchiverRepository(
        1,
        config,
        retention,
        indexPrefix,
        processInstanceIndex,
        batchOperationIndex,
        zeebeIndexPrefix,
        client,
        Runnable::run,
        metrics,
        LOGGER);
  }

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create(searchDB.esUrl())).build();
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
