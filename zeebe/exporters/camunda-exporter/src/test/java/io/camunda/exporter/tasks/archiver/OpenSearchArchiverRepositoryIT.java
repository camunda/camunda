/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static io.camunda.search.test.utils.SearchDBExtension.ARCHIVER_IDX_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static io.camunda.search.test.utils.SearchDBExtension.create;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.config.IndexRetentionPolicy;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest.Builder;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

@SuppressWarnings("resource")
final class OpenSearchArchiverRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchArchiverRepositoryIT.class);
  @RegisterExtension private static SearchDBExtension searchDB = create();
  private static final ObjectMapper MAPPER = TestObjectMapper.objectMapper();
  @AutoClose private final RestClientTransport transport = createRestClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoryConfiguration config = new HistoryConfiguration();
  private final ConnectConfiguration connectConfiguration = new ConnectConfiguration();
  private final RetentionConfiguration retention = new RetentionConfiguration();
  private final String processInstanceIndex =
      ARCHIVER_IDX_PREFIX + "process-instance-" + UUID.randomUUID();
  private final String batchOperationIndex =
      ARCHIVER_IDX_PREFIX + "batch-operation-" + UUID.randomUUID();
  private final OpenSearchClient testClient = createOpenSearchClient();
  private final String zeebeIndexPrefix = "zeebe-record";
  private final String zeebeIndex = zeebeIndexPrefix + "-" + UUID.randomUUID();

  @AfterEach
  void afterEach() throws IOException {
    final DeleteIndexRequest deleteRequest = new Builder().index("*").build();
    testClient.indices().delete(deleteRequest);

    // delete all policies created during the tests
    deleteAllTestPolicies();
  }

  @Test
  void shouldDeleteDocuments() throws IOException {
    // given
    final var indexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
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
    final var indexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
    final var repository = createRepository();
    testClient.indices().create(r -> r.index(indexName));

    retention.setEnabled(true);
    retention.setPolicyName("operate_delete_archived_indices");

    // when
    createLifeCyclePolicies();
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Takes a while for the policy to be applied
    Awaitility.await("until the policy has been visibly applied")
        .untilAsserted(
            () -> assertThat(fetchPolicyForIndex(indexName)).isEqualTo(retention.getPolicyName()));
  }

  @Test
  void shouldApplyCustomPoliciesToIndices() throws IOException {
    // given
    final var formattedPrefix =
        AbstractIndexDescriptor.formatIndexPrefix(connectConfiguration.getIndexPrefix());
    final var indices =
        List.of(
            formattedPrefix + "camunda-user-task-8.8.0_2025-06-10",
            formattedPrefix + "tasklist-user-task-8.8.0_2025-06-10",
            formattedPrefix + "operate-list-view-8.3.0_2024-06-02",
            formattedPrefix + "tasklist-task-8.5.0_2024-06-02");

    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_policy");
    retention.setIndexPolicies(
        List.of(
            new IndexRetentionPolicy(
                "user_task_policy", "7d", List.of("camunda-user-task", "tasklist-user-task")),
            new IndexRetentionPolicy("operate_policy", "30d", List.of("operate.*"))));

    createLifeCyclePolicies();
    for (final var index : indices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setIndexLifeCycle(indices.toArray(String[]::new));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "camunda-user-task-8.8.0_2025-06-10"))
        .isEqualTo("user_task_policy");
    assertThat(
            fetchPolicyForIndexWithAwait(formattedPrefix + "tasklist-user-task-8.8.0_2025-06-10"))
        .isEqualTo("user_task_policy");
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "operate-list-view-8.3.0_2024-06-02"))
        .isEqualTo("operate_policy");
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "tasklist-task-8.5.0_2024-06-02"))
        .isEqualTo("default_policy");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policy modification not allowed")
  void shouldSetIndexLifeCycleOnAllValidIndexes(final String prefix) throws IOException {
    // given
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(prefix);
    final var expectedIndices =
        List.of(
            formattedPrefix + "operate-record-8.2.1_2024-01-02",
            formattedPrefix + "tasklist-record-8.3.0_2024-01");
    final var untouchedIndices =
        new ArrayList<>(
            List.of(
                formattedPrefix + "operate-record-8.2.1_",
                formattedPrefix + "other-" + "tasklist-record-8.3.0_"));

    // we cannot test the case with multiple different prefixes when no prefix is given, since it
    // will just match everything from the other prefixes...
    if (!prefix.isEmpty()) {
      untouchedIndices.add("other-" + "tasklist-record-8.3.0_2024-01-02");
    }

    connectConfiguration.setIndexPrefix(prefix);
    final var repository = createRepository();
    final var indices = new ArrayList<>(expectedIndices);
    indices.addAll(untouchedIndices);

    retention.setEnabled(true);
    retention.setPolicyName(prefix + "operate_delete_archived_indices");

    createLifeCyclePolicies();
    for (final var index : indices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    for (final var index : expectedIndices) {
      // In OS, it takes a little while for the policy to be visibly applied, and flushing seems to
      // have no effect on that
      Awaitility.await("until the policy has been visibly applied")
          .untilAsserted(
              () ->
                  assertThat(fetchPolicyForIndex(index))
                      .as("policy applied for %s", index)
                      .isNotNull()
                      .isEqualTo(prefix + "operate_delete_archived_indices"));
    }

    for (final var index : untouchedIndices) {
      assertThat(fetchPolicyForIndex(index)).as("no policy applied to %s", index).isEqualTo("null");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policy modification not allowed")
  void shouldApplyCustomIndexPoliciesWithExactMatching(final String prefix) throws IOException {
    // given
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(prefix);
    final var expectedIndices =
        List.of(
            formattedPrefix + "camunda-user-task-8.8.0_2025-01-10",
            formattedPrefix + "camunda-user-task-at-8.8.0_2025-01-10",
            formattedPrefix + "operate-list-view-8.3.0_2024-01-02",
            formattedPrefix + "tasklist-task-8.5.0_2024-01-02");

    connectConfiguration.setIndexPrefix(prefix);
    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_policy");

    // Configure exact index name matches
    retention.setIndexPolicies(
        List.of(
            new IndexRetentionPolicy("user_task_policy", "7d", List.of("camunda-user-task")),
            new IndexRetentionPolicy(
                "user_task_at_policy", "14d", List.of("camunda-user-task-at"))));

    createLifeCyclePolicies();
    for (final var index : expectedIndices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Verify exact matches get their specific policies
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "camunda-user-task-8.8.0_2025-01-10"))
        .isEqualTo("user_task_policy");
    assertThat(
            fetchPolicyForIndexWithAwait(formattedPrefix + "camunda-user-task-at-8.8.0_2025-01-10"))
        .isEqualTo("user_task_at_policy");

    // Verify indices without custom policies get default policy
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "operate-list-view-8.3.0_2024-01-02"))
        .isEqualTo("default_policy");
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "tasklist-task-8.5.0_2024-01-02"))
        .isEqualTo("default_policy");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policy modification not allowed")
  void shouldApplyCustomIndexPoliciesWithPatternMatching(final String prefix) throws IOException {
    // given
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(prefix);
    final var expectedIndices =
        List.of(
            formattedPrefix + "camunda-user-task-8.8.0_2025-03-10",
            formattedPrefix + "camunda-user-task-at-8.8.0_2025-03-10",
            formattedPrefix + "camunda-authorization-8.8.0_2025-03-10",
            formattedPrefix + "operate-list-view-8.3.0_2024-03-02",
            formattedPrefix + "operate-process-8.3.0_2024-03-02",
            formattedPrefix + "tasklist-task-8.5.0_2024-03-02",
            formattedPrefix + "other-index-8.8.0_2025-03-10");

    connectConfiguration.setIndexPrefix(prefix);
    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_policy");

    // Configure pattern-based policies
    retention.setIndexPolicies(
        List.of(
            new IndexRetentionPolicy("camunda_policy", "40d", List.of("camunda.*")),
            new IndexRetentionPolicy(
                "operate_policy", "60d", List.of("operate-list-view", "operate-process")),
            new IndexRetentionPolicy("tasklist_policy", "90d", List.of("tasklist.*"))));

    createLifeCyclePolicies();
    for (final var index : expectedIndices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Verify pattern matches get their specific policies
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "camunda-user-task-8.8.0_2025-03-10"))
        .isEqualTo("camunda_policy");
    assertThat(
            fetchPolicyForIndexWithAwait(formattedPrefix + "camunda-user-task-at-8.8.0_2025-03-10"))
        .isEqualTo("camunda_policy");
    assertThat(
            fetchPolicyForIndexWithAwait(
                formattedPrefix + "camunda-authorization-8.8.0_2025-03-10"))
        .isEqualTo("camunda_policy");

    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "operate-list-view-8.3.0_2024-03-02"))
        .isEqualTo("operate_policy");
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "operate-process-8.3.0_2024-03-02"))
        .isEqualTo("operate_policy");

    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "tasklist-task-8.5.0_2024-03-02"))
        .isEqualTo("tasklist_policy");

    // Verify indices without matching patterns get default policy
    assertThat(fetchPolicyForIndexWithAwait(formattedPrefix + "other-index-8.8.0_2025-03-10"))
        .isEqualTo("default_policy");
  }

  @Test
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policy modification not allowed")
  void shouldApplyLastMatchingPatternWhenMultiplePatternsMatch() throws IOException {
    // given
    connectConfiguration.setIndexPrefix("test");
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix("test");
    final var testIndex = formattedPrefix + "user-activity-logs-8.8.0_2025-08-10";

    final var repository = createRepository();
    retention.setEnabled(true);
    retention.setPolicyName("default_policy");

    // Configure overlapping patterns, the implementation should apply the last one that matches
    retention.setIndexPolicies(
        List.of(
            new IndexRetentionPolicy("user_general_policy", "1d", List.of("user.*")),
            new IndexRetentionPolicy("user_activity_policy", "2d", List.of("user-activity.*")),
            new IndexRetentionPolicy("logs_policy", "3d", List.of(".*logs"))));

    createLifeCyclePolicies();
    testClient.indices().create(r -> r.index(testIndex));

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    final var appliedPolicy = fetchPolicyForIndexWithAwait(testIndex);
    assertThat(appliedPolicy)
        .as("Index should have the last policy that matched its name")
        .isEqualTo("logs_policy");
  }

  @Test
  void shouldReindexDocuments() throws IOException {
    // given
    final var sourceIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
    final var destIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
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
    final var sourceIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
    final var destIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
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
    assertThat(batch.ids()).containsExactlyInAnyOrder("1", "2");
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
  }

  @Test
  void shouldSetTheCorrectFinishDateWithRollover() throws IOException {
    Assumptions.assumeTrue(
        System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isEmpty(),
        "Skipping test if AWS is used. See https://github.com/camunda/camunda/pull/35591");
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
    Assumptions.assumeTrue(
        System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isEmpty(),
        "Skipping test if AWS is used. See https://github.com/camunda/camunda/pull/35591");
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
    Assumptions.assumeTrue(
        System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isEmpty(),
        "Skipping test if AWS is used. See https://github.com/camunda/camunda/pull/35591");

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

  private <T extends TDocument> void index(final String index, final T document) {
    try {
      testClient.index(b -> b.index(index).document(document).id(document.id()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void createLifeCyclePolicies() {
    createLifeCyclePolicy(retention.getPolicyName(), retention.getMinimumAge());

    retention
        .getIndexPolicies()
        .forEach(policy -> createLifeCyclePolicy(policy.getPolicyName(), policy.getMinimumAge()));
  }

  private void createLifeCyclePolicy(final String policyName, final String minAge) {
    final var engineClient = new OpensearchEngineClient(testClient, MAPPER);
    try {
      engineClient.putIndexLifeCyclePolicy(policyName, minAge);
    } catch (final Exception e) {
      LOGGER.warn("Could not create life cycle policy", e);
    }
  }

  private String fetchPolicyForIndex(final String indexName) {
    final var genericClient =
        new OpenSearchGenericClient(testClient._transport(), testClient._transportOptions());
    final var request =
        Requests.builder().method("get").endpoint("_plugins/_ism/explain/" + indexName).build();
    try {
      final var response = genericClient.execute(request);
      final var jsonString = response.getBody().orElseThrow().bodyAsString();
      final var json = MAPPER.readTree(jsonString);
      final var index = json.get(indexName);
      if (index == null) {
        throw new AssertionError(
            "Failed to explain non-existent index '%s'; see response: %s"
                .formatted(indexName, jsonString));
      }

      return index.findPath("index.plugins.index_state_management.policy_id").asText();
    } catch (final IOException e) {
      throw new AssertionError("Failed to fetch policy for index " + indexName, e);
    }
  }

  private String fetchPolicyForIndexWithAwait(final String indexName) {
    return Awaitility.await("until the policy has been visibly applied")
        .until(() -> fetchPolicyForIndex(indexName), policy -> !policy.equals("null"));
  }

  private void deleteAllTestPolicies() {
    final var genericClient =
        new OpenSearchGenericClient(testClient._transport(), testClient._transportOptions());
    try {
      // Get all policies
      final var listRequest =
          Requests.builder().method("GET").endpoint("_plugins/_ism/policies").build();
      final var listResponse = genericClient.execute(listRequest);
      final var jsonString = listResponse.getBody().orElseThrow().bodyAsString();
      final var json = MAPPER.readTree(jsonString);

      // Extract policy names and delete each one
      final var policies = json.get("policies");
      if (policies != null && policies.isArray()) {
        for (final var policy : policies) {
          final var policyId = policy.get("_id");
          if (policyId != null) {
            final var policyName = policyId.asText();
            try {
              final var deleteRequest =
                  Requests.builder()
                      .method("DELETE")
                      .endpoint("_plugins/_ism/policies/" + policyName)
                      .build();
              genericClient.execute(deleteRequest);
              LOGGER.debug("Deleted ISM policy: {}", policyName);
            } catch (final Exception e) {
              LOGGER.warn("Could not delete ISM policy '{}': {}", policyName, e.getMessage());
            }
          }
        }
      }
    } catch (final Exception e) {
      LOGGER.warn("Could not delete test ISM policies", e);
    }
  }

  // no need to close resource returned here, since the transport is closed above anyway
  private OpenSearchArchiverRepository createRepository() {
    final var client = createOpenSearchAsyncClient();
    final var metrics = new CamundaExporterMetrics(meterRegistry);

    return new OpenSearchArchiverRepository(
        1,
        config,
        retention,
        connectConfiguration.getIndexPrefix(),
        processInstanceIndex,
        batchOperationIndex,
        zeebeIndexPrefix,
        client,
        Runnable::run,
        metrics,
        LOGGER);
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

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create(searchDB.osUrl())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }

  private OpenSearchClient createOpenSearchClient() {
    final var isAWSRun = System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "");
    if (isAWSRun.isEmpty()) {
      return new OpenSearchClient(transport);
    } else {
      final URI uri = URI.create(isAWSRun);
      final SdkHttpClient httpClient = ApacheHttpClient.builder().build();
      final var region = new DefaultAwsRegionProviderChain().getRegion();
      return new OpenSearchClient(
          new AwsSdk2Transport(
              httpClient,
              uri.getHost(),
              region,
              AwsSdk2TransportOptions.builder()
                  .setMapper(new JacksonJsonpMapper(new ObjectMapper()))
                  .build()));
    }
  }

  private OpenSearchAsyncClient createOpenSearchAsyncClient() {
    final var isAWSRun = System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "");
    if (isAWSRun.isEmpty()) {
      return new OpenSearchAsyncClient(transport);
    } else {
      final URI uri = URI.create(isAWSRun);
      final SdkHttpClient httpClient = ApacheHttpClient.builder().build();
      final var region = new DefaultAwsRegionProviderChain().getRegion();
      return new OpenSearchAsyncClient(
          new AwsSdk2Transport(
              httpClient,
              uri.getHost(),
              region,
              AwsSdk2TransportOptions.builder()
                  .setMapper(new JacksonJsonpMapper(new ObjectMapper()))
                  .build()));
    }
  }

  private record TestBatchOperation(String id, String endDate) implements TDocument {}

  private record TestDocument(String id) implements TDocument {}

  private record TestProcessInstance(
      String id, String endDate, String joinRelation, int partitionId) implements TDocument {}

  private interface TDocument {
    String id();
  }
}
