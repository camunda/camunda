/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.indices.IndexSettingsLifecycle;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
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
import java.util.Random;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @AutoClose private final RestClientTransport transport = createRestClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoryConfiguration config = new HistoryConfiguration();
  private final RetentionConfiguration retention = new RetentionConfiguration();
  private String indexPrefix;
  private final String zeebeIndexPrefix = "zeebe-record";
  private String processInstanceIndex;
  private String batchOperationIndex;
  private String auditLogIndex;
  private TestExporterResourceProvider resourceProvider;
  private final String zeebeIndex = zeebeIndexPrefix + "-" + UUID.randomUUID();
  private final ElasticsearchClient testClient = new ElasticsearchClient(transport);
  private final ObjectMapper objectMapper = TestObjectMapper.objectMapper();

  @AfterEach
  void afterEach() throws IOException {
    // wipes all data in ES between tests
    final var response = transport.restClient().performRequest(new Request("DELETE", "_all"));
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
  }

  @BeforeEach
  void beforeEach() {
    config.setRetention(retention);
    indexPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    resourceProvider = new TestExporterResourceProvider(indexPrefix, true);
    processInstanceIndex =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class).getFullQualifiedName();
    batchOperationIndex =
        resourceProvider
            .getIndexTemplateDescriptor(BatchOperationTemplate.class)
            .getFullQualifiedName();
    auditLogIndex =
        resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class).getFullQualifiedName();
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
            indexName, Map.of("id", documents.stream().limit(2).map(TestDocument::id).toList()));

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
  void shouldDeleteDocumentsWithFilters() throws IOException {
    // given
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestAuditLogDocument("1", "A"),
            new TestAuditLogDocument("2", "B"),
            new TestAuditLogDocument("3", "A"));
    createAuditLogIndex();
    documents.forEach(doc -> index(auditLogIndex, doc));
    testClient.indices().refresh(r -> r.index(auditLogIndex));

    // when - delete documents with ids [1, 2, 3], but only if the type is "A"
    final var result =
        repository.deleteDocuments(
            auditLogIndex, Map.of("id", List.of("1", "2", "3")), Map.of("entityType", "A"));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(auditLogIndex));
    final var remaining =
        testClient.search(
            r -> r.index(auditLogIndex).requestCache(false).query(q -> q.matchAll(m -> m)),
            TestAuditLogDocument.class);
    assertThat(remaining.hits().hits())
        .as("documents 1,3 should be deleted")
        .hasSize(1)
        .first()
        .extracting(h -> h.source().id())
        .isEqualTo("2");
  }

  @Test
  void shouldReindexDocumentsWithFilters() throws IOException {
    // given
    final var sourceIndexName = UUID.randomUUID().toString();
    final var destIndexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestAuditLogDocument("1", "A"),
            new TestAuditLogDocument("2", "B"),
            new TestAuditLogDocument("3", "A"));
    createAuditLogIndex(sourceIndexName);
    createAuditLogIndex(destIndexName);
    documents.forEach(doc -> index(sourceIndexName, doc));
    testClient.indices().refresh(r -> r.index(sourceIndexName));

    // when - reindex documents with id "1" or "2", but only if the type is "A"
    final var result =
        repository.reindexDocuments(
            sourceIndexName,
            destIndexName,
            Map.of("id", List.of("1", "2", "3")),
            Map.of("entityType", "A"));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(destIndexName));
    final var reindexed =
        testClient.search(
            r -> r.index(destIndexName).requestCache(false).query(q -> q.matchAll(m -> m)),
            TestAuditLogDocument.class);
    assertThat(reindexed.hits().hits())
        .as("documents 1,3 should be reindexed")
        .hasSize(2)
        .extracting(h -> h.source().id())
        .containsExactlyInAnyOrder("1", "3");
  }

  @Test
  void shouldMoveDocumentsWithFilters() throws IOException {
    // given
    final var sourceIndexName = UUID.randomUUID().toString();
    final var destIndexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestAuditLogDocument("1", "A"),
            new TestAuditLogDocument("2", "B"),
            new TestAuditLogDocument("3", "A"));
    createAuditLogIndex(sourceIndexName);
    createAuditLogIndex(destIndexName);
    documents.forEach(doc -> index(sourceIndexName, doc));
    testClient.indices().refresh(r -> r.index(sourceIndexName));

    // when
    final var result =
        repository.moveDocuments(
            sourceIndexName,
            destIndexName,
            Map.of("id", List.of("1", "2", "3")),
            Map.of("entityType", "A"),
            Runnable::run);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(sourceIndexName, destIndexName));

    final var remaining =
        testClient.search(
            r -> r.index(sourceIndexName).requestCache(false).query(q -> q.matchAll(m -> m)),
            TestAuditLogDocument.class);
    assertThat(remaining.hits().hits())
        .as("only document 2 should remain")
        .hasSize(1)
        .extracting(h -> h.source().id())
        .containsExactlyInAnyOrder("2");

    final var moved =
        testClient.search(
            r -> r.index(destIndexName).requestCache(false).query(q -> q.matchAll(m -> m)),
            TestAuditLogDocument.class);
    assertThat(moved.hits().hits())
        .as("documents 1,3 should be moved")
        .hasSize(2)
        .extracting(h -> h.source().id())
        .containsExactlyInAnyOrder("1", "3");
  }

  @Test
  void shouldSetIndexLifeCycle() throws IOException {
    // given
    final var taskIndex =
        resourceProvider.getIndexTemplateDescriptor(TaskTemplate.class).getFullQualifiedName()
            + UUID.randomUUID();
    final var repository = createRepository();

    testClient.indices().create(r -> r.index(taskIndex));

    final var initialLifecycle = getLifeCycle(taskIndex);
    assertThat(initialLifecycle).isNull();

    retention.setEnabled(true);
    putLifecyclePolicies();

    // when
    final var result = repository.setIndexLifeCycle(taskIndex);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    assertThat(getLifeCycle(taskIndex))
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("camunda-retention-policy");
  }

  @ParameterizedTest
  @ValueSource(classes = {UsageMetricTemplate.class, UsageMetricTUTemplate.class})
  void shouldSetIndexLifeCycleForUsageMetric(
      final Class<? extends AbstractTemplateDescriptor> descriptorClass) throws IOException {
    // given
    final var usageMetricIndex =
        resourceProvider.getIndexTemplateDescriptor(descriptorClass).getFullQualifiedName()
            + UUID.randomUUID();
    final var repository = createRepository();

    testClient.indices().create(r -> r.index(usageMetricIndex));

    final var initialLifecycle = getLifeCycle(usageMetricIndex);
    assertThat(initialLifecycle).isNull();

    retention.setEnabled(true);
    putLifecyclePolicies();

    // when
    final var result = repository.setIndexLifeCycle(usageMetricIndex);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    assertThat(getLifeCycle(usageMetricIndex))
        .isNotNull()
        .extracting(IndexSettingsLifecycle::name)
        .isEqualTo("camunda-usage-metrics-retention-policy");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test"})
  void shouldSetIndexLifeCycleOnAllValidIndexes(final String prefix) throws IOException {
    // given
    indexPrefix = prefix;
    resourceProvider = new TestExporterResourceProvider(indexPrefix, true);
    processInstanceIndex =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class).getFullQualifiedName();
    batchOperationIndex =
        resourceProvider
            .getIndexTemplateDescriptor(BatchOperationTemplate.class)
            .getFullQualifiedName();
    final var usageMetricIndex =
        resourceProvider
            .getIndexTemplateDescriptor(UsageMetricTemplate.class)
            .getFullQualifiedName();
    final var usageMetricTUIndex =
        resourceProvider
            .getIndexTemplateDescriptor(UsageMetricTUTemplate.class)
            .getFullQualifiedName();

    final var usageMetricsIndices =
        List.of(usageMetricIndex + "2024-01-02", usageMetricTUIndex + "2024-01-02");

    final var historicalIndices =
        List.of(processInstanceIndex + "2024-01-02", batchOperationIndex + "2024-01");
    final var untouchedIndices =
        new ArrayList<>(List.of(processInstanceIndex, batchOperationIndex));

    // we cannot test the case with multiple different prefixes when no prefix is given, since it
    // will just match everything from the other prefixes...
    if (!prefix.isEmpty()) {
      untouchedIndices.add("other-" + "tasklist-task-8.8.0_2024-01-02");
    }

    final var repository = createRepository();
    final var indices = new ArrayList<String>();
    indices.addAll(historicalIndices);
    indices.addAll(usageMetricsIndices);
    indices.addAll(untouchedIndices);

    retention.setEnabled(true);
    retention.setPolicyName("default-policy");
    retention.setUsageMetricsPolicyName("custom-usage-metrics-policy");

    putLifecyclePolicies();
    for (final var index : indices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    for (final var index : usageMetricsIndices) {
      assertThat(getLifeCycle(index))
          .isNotNull()
          .extracting(IndexSettingsLifecycle::name)
          .isEqualTo("custom-usage-metrics-policy");
    }

    // verify that the default policy was applied to all other indices
    for (final var index : historicalIndices) {
      assertThat(getLifeCycle(index))
          .isNotNull()
          .extracting(IndexSettingsLifecycle::name)
          .isEqualTo("default-policy");
    }
    for (final var index : untouchedIndices) {
      assertThat(getLifeCycle(index)).as("no policy applied to %s", index).isNull();
    }
  }

  @Test
  void shouldNotFailSettingILMOnMissingIndex() throws IOException {
    // given
    final var repository = createRepository();
    final var indexName = processInstanceIndex + UUID.randomUUID();
    retention.setEnabled(true);
    retention.setPolicyName("operate_delete_archived_indices");
    putLifecyclePolicies();

    // when
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
  }

  private IndexSettingsLifecycle getLifeCycle(final String indexName) throws IOException {
    return testClient
        .indices()
        .getSettings(r -> r.index(indexName))
        .get(indexName)
        .settings()
        .index()
        .lifecycle();
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
            Map.of("id", documents.stream().limit(2).map(TestDocument::id).toList()));

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
            Map.of("id", documents.stream().limit(2).map(TestDocument::id).toList()),
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
                "1", twoHoursAgo, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 1, null, null),
            new TestProcessInstance(
                "2", twoHoursAgo, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 2, null, null),
            new TestProcessInstance(
                "3", twoHoursAgo, ListViewTemplate.ACTIVITIES_JOIN_RELATION, 1, null, null),
            new TestProcessInstance(
                "4",
                now.toString(),
                ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION,
                1,
                null,
                null));

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
    assertThat(batch.processInstanceKeys()).containsExactly(1L);
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
  }

  @Test
  void shouldHandlePIMode() throws Exception {
    // given
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI);
    config.setRolloverBatchSize(100);
    config.setWaitPeriodBeforeArchiving("0s");
    final var repository = createRepository();

    createProcessInstanceIndex();

    // Doc 10: Legacy (No root, No parent)
    indexProcessInstance("10", "2020-01-01", null, null);
    // Doc 20: New Root (Root=100, No parent)
    indexProcessInstance("20", "2020-01-01", 100L, null);
    // Doc 30: New Child (Root=100, Parent=100)
    indexProcessInstance("30", "2020-01-01", 100L, 100L);

    testClient.indices().refresh(r -> r.index(processInstanceIndex));

    // when
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then
    // PI mode: Should select all 3 (since end date matches).
    // And should treat ALL as legacyProcessInstanceKeys (PROCESS_INSTANCE_KEY)
    assertThat(batch.processInstanceKeys()).containsExactlyInAnyOrder(10L, 20L, 30L);
    assertThat(batch.rootProcessInstanceKeys()).isEmpty();
  }

  @Test
  void shouldHandlePIHierarchyMode() throws Exception {
    // given
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI_HIERARCHY);
    config.setRolloverBatchSize(100);
    config.setWaitPeriodBeforeArchiving("0s");
    final var repository = createRepository();

    createProcessInstanceIndex();

    // Doc 10: Legacy (No root, No parent) -> Should be selected as legacy
    indexProcessInstance("10", "2020-01-01", null, null);
    // Doc 20: New Root (Root=100, No parent) -> Should be selected as new root
    indexProcessInstance("20", "2020-01-01", 100L, null);
    // Doc 30: New Child (Root=100, Parent=100) -> Should NOT be selected (it's a child)
    indexProcessInstance("30", "2020-01-01", 100L, 100L);

    testClient.indices().refresh(r -> r.index(processInstanceIndex));

    // when
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then
    // Legacy -> "10"
    assertThat(batch.processInstanceKeys()).containsExactly(10L);

    // New Hierarchy -> "100" (rootProcessInstanceKey)
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(100L);
  }

  @Test
  void shouldHandlePIHierarchyIgnoreLegacyMode() throws Exception {
    // given
    config.setProcessInstanceRetentionMode(ProcessInstanceRetentionMode.PI_HIERARCHY_IGNORE_LEGACY);
    config.setRolloverBatchSize(100);
    config.setWaitPeriodBeforeArchiving("0s");
    final var repository = createRepository();

    createProcessInstanceIndex();

    // Doc 10: Legacy (No root, No parent) -> Should NOT be selected
    indexProcessInstance("10", "2020-01-01", null, null);
    // Doc 20: New Root (Root=100, No parent) -> Should be selected as new root
    indexProcessInstance("20", "2020-01-01", 100L, null);
    // Doc 30: New Child (Root=100, Parent=100) -> Should NOT be selected
    indexProcessInstance("30", "2020-01-01", 100L, 100L);

    testClient.indices().refresh(r -> r.index(processInstanceIndex));

    // when
    final var batch = repository.getProcessInstancesNextBatch().join();

    // then
    assertThat(batch.processInstanceKeys()).isEmpty();
    // New Hierarchy -> "100"
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(100L);
  }

  private void indexProcessInstance(
      final String id, final String endDate, final Long rootPI, final Long parentPI) {
    // create the index template first to ensure ID is a keyword, otherwise the surrounding
    // aggregation will fail
    final var doc =
        new TestProcessInstance(
            id, endDate, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 1, rootPI, parentPI);
    index(processInstanceIndex, doc);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void shouldGetUsageMetricNextBatch(final int partitionId) throws IOException {
    // given
    final var now = Instant.now();
    final var twoHoursAgo = now.minus(Duration.ofHours(2)).toString();
    final var repository = createRepository(partitionId);
    final var usageMetricIndex =
        resourceProvider
            .getIndexTemplateDescriptor(UsageMetricTemplate.class)
            .getFullQualifiedName();
    createUsageMetricIndex(usageMetricIndex);
    final var documents =
        List.of(
            new TestUsageMetric("1", twoHoursAgo, 1),
            new TestUsageMetric("2", twoHoursAgo, 1),
            new TestUsageMetric("3", twoHoursAgo, -1),
            new TestUsageMetric("5", now.toString(), 1),
            new TestUsageMetric("20", now.toString(), 2),
            new TestUsageMetric("21", twoHoursAgo, 2));
    documents.forEach(doc -> index(usageMetricIndex, doc));
    testClient.indices().refresh(r -> r.index(usageMetricIndex));
    config.setRolloverBatchSize(5);

    // when
    final var result = repository.getUsageMetricNextBatch();

    // then
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    if (partitionId == 1) {
      assertThat(batch.ids()).containsExactly("1", "2", "3");
    } else {
      assertThat(batch.ids()).containsExactly("21");
    }
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void shouldGetUsageMetricTUNextBatch(final int partitionId) throws IOException {
    // given
    final var now = Instant.now();
    final var twoHoursAgo = now.minus(Duration.ofHours(2)).toString();
    final var repository = createRepository(partitionId);
    final var usageMetricTUIndex =
        resourceProvider
            .getIndexTemplateDescriptor(UsageMetricTUTemplate.class)
            .getFullQualifiedName();
    createUsageMetricTUIndex(usageMetricTUIndex);
    final var documents =
        List.of(
            new TestUsageMetricTU("10", twoHoursAgo, 1),
            new TestUsageMetricTU("11", twoHoursAgo, 1),
            new TestUsageMetricTU("12", twoHoursAgo, -1),
            new TestUsageMetricTU("14", now.toString(), 1),
            new TestUsageMetricTU("20", now.toString(), 2),
            new TestUsageMetricTU("21", twoHoursAgo, 2));
    documents.forEach(doc -> index(usageMetricTUIndex, doc));
    testClient.indices().refresh(r -> r.index(usageMetricTUIndex));
    config.setRolloverBatchSize(5);

    // when
    final var result = repository.getUsageMetricTUNextBatch();

    // then
    final var dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    if (partitionId == 1) {
      assertThat(batch.ids()).containsExactly("10", "11", "12");
    } else {
      assertThat(batch.ids()).containsExactly("21");
    }
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
  void shouldGetStandaloneDecisionNextBatch() throws IOException {
    // given - 5 documents, two of which were created over an hour ago and should be archived,
    // one of which was created recently, one on a different partition and one with a different
    // process instance key
    final var now = Instant.now();
    final var twoHoursAgo = now.minus(Duration.ofHours(2)).toString();
    final var repository = createRepository();
    final var documents =
        List.of(
            new TestStandaloneDecision("1", twoHoursAgo, 1, -1),
            new TestStandaloneDecision("2", twoHoursAgo, 1, -1),
            new TestStandaloneDecision("3", twoHoursAgo, 2, -1),
            new TestStandaloneDecision("4", twoHoursAgo, 1, 12345),
            new TestStandaloneDecision("5", now.toString(), 1, -1));

    // create the index template first to ensure ID is a keyword, otherwise the surrounding
    // aggregation will fail
    final var standaloneDecisionIndex =
        resourceProvider
            .getIndexTemplateDescriptor(DecisionInstanceTemplate.class)
            .getFullQualifiedName();
    createStandaloneDecisionIndex(standaloneDecisionIndex);
    documents.forEach(doc -> index(standaloneDecisionIndex, doc));
    testClient.indices().refresh(r -> r.index(standaloneDecisionIndex));
    config.setRolloverBatchSize(3);

    // when
    final var result = repository.getStandaloneDecisionNextBatch();

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
        .untilAsserted(
            () -> {
              assertThat(firstBatch.isDone()).isTrue();
              assertThat(firstBatch.get().ids()).containsExactly("1", "2");
              assertThat(firstBatch.get().finishDate())
                  .isEqualTo(dateFormatter.format(now.minus(Duration.ofDays(4))));
            });
    repository
        .moveDocuments(
            batchOperationIndex, destIndexName, Map.of("id", List.of("1", "2")), Runnable::run)
        .join();

    final var secondBatchDocuments =
        List.of(new TestBatchOperation("3", twoDaysAgo), new TestBatchOperation("4", twoDaysAgo));

    secondBatchDocuments.forEach(doc -> index(batchOperationIndex, doc));
    testClient.indices().refresh(r -> r.index(batchOperationIndex));

    // then
    final var secondBatch = repository.getBatchOperationsNextBatch();
    Awaitility.await("waiting for second batch operation to be complete")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(secondBatch.isDone()).isTrue();
              assertThat(secondBatch.get().ids()).containsExactly("3", "4");
              assertThat(secondBatch.get().finishDate())
                  .isEqualTo(dateFormatter.format(now.minus(Duration.ofDays(4))));
            });
    // it should still have the same finish date since the rollover window is three days, and the
    // difference of both batches is only 2 days.
    repository
        .moveDocuments(
            batchOperationIndex, destIndexName, Map.of("id", List.of("3", "4")), Runnable::run)
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
        .untilAsserted(
            () -> {
              assertThat(thirdBatch.isDone()).isTrue();
              assertThat(thirdBatch.get().ids()).containsExactly("5", "6");
              assertThat(thirdBatch.get().finishDate())
                  .isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
            });
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
    assertThat(batch.ids()).containsExactly("1", "2");
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
    assertThat(batch.ids()).containsExactly("1", "2");
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofDays(1))));
  }

  private <T extends TDocument> void index(final String index, final T document) {
    try {
      testClient.index(b -> b.index(index).document(document).id(document.id()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void shouldCacheIndicesWhichHaveRetentionPolicyAppliedAndNotReapplyPointlessly() {
    // given
    retention.setEnabled(true);
    final var indexName1 = processInstanceIndex + UUID.randomUUID();
    final var indexName2 = processInstanceIndex + UUID.randomUUID();

    final var clientSpy = spy(new ElasticsearchAsyncClient(transport));
    final var indicesClientSpy = spy(clientSpy.indices());

    doReturn(indicesClientSpy).when(clientSpy).indices();

    final var repository = createRepository(clientSpy);

    // when - first time setting policy for indexName2 it should make the put settings for
    // indexName2
    repository.setIndexLifeCycle(indexName2);

    final ArgumentCaptor<PutIndicesSettingsRequest> captor =
        ArgumentCaptor.forClass(PutIndicesSettingsRequest.class);

    Awaitility.await().untilAsserted(() -> verify(indicesClientSpy).putSettings(captor.capture()));

    final var putIndicesSettingsRequest = captor.getValue();
    assertThat(putIndicesSettingsRequest.index()).containsExactly(indexName2);

    reset(indicesClientSpy);

    // setting policy first time for indexName1 but second time for indexName2, it
    // should have cached the fact that indexName2 already has a policy and not be included in
    // the request.
    repository.setIndexLifeCycle(indexName1);
    repository.setIndexLifeCycle(indexName2);

    // then
    final var captor2 = ArgumentCaptor.forClass(PutIndicesSettingsRequest.class);

    Awaitility.await()
        .untilAsserted(() -> verify(indicesClientSpy, times(1)).putSettings(captor2.capture()));

    final var putIndicesSettingsRequest2 = captor2.getValue();
    assertThat(putIndicesSettingsRequest2.index()).containsExactly(indexName1);
  }

  @Test
  void shouldReapplyILMPolicyAfterRetentionPeriodExpiration() throws Exception {
    // given
    retention.setEnabled(true);
    final int minimumAgeSeconds = 2;
    retention.setMinimumAge("%ds".formatted(minimumAgeSeconds));
    final var indexName1 = processInstanceIndex + UUID.randomUUID();
    final var indexName2 = processInstanceIndex + UUID.randomUUID();

    final var clientSpy = spy(new ElasticsearchAsyncClient(transport));
    final var indicesClientSpy = spy(clientSpy.indices());

    doReturn(indicesClientSpy).when(clientSpy).indices();

    final var repository = createRepository(clientSpy);

    // when - first setting policy for indexName1 and indexName2
    repository
        .setIndexLifeCycle(indexName1)
        .thenApply(
            ignore -> {
              // wait for the cache of indexName1 to expire
              Awaitility.await().pollDelay(Duration.ofSeconds(minimumAgeSeconds)).until(() -> true);
              return repository.setIndexLifeCycle(indexName2);
            })
        .get();

    final ArgumentCaptor<PutIndicesSettingsRequest> captor =
        ArgumentCaptor.forClass(PutIndicesSettingsRequest.class);

    verify(indicesClientSpy, times(2)).putSettings(captor.capture());

    final var requests = captor.getAllValues();
    assertThat(requests)
        .map(PutIndicesSettingsRequest::index)
        .containsExactly(List.of(indexName1), List.of(indexName2));

    // resetting the spy to ensure no more interactions are recorded
    reset(indicesClientSpy);

    // setting policy second time for indexName1 and indexName2
    repository.setIndexLifeCycle(indexName1).get();
    repository.setIndexLifeCycle(indexName2).get();

    // then - only indexName1 should be included in the request, since the cache for it has expired
    final var captor2 = ArgumentCaptor.forClass(PutIndicesSettingsRequest.class);
    verify(indicesClientSpy, times(1)).putSettings(captor2.capture());
    final var putIndicesSettingsRequest2 = captor2.getValue();
    assertThat(putIndicesSettingsRequest2.index()).containsExactly(indexName1);
  }

  @Test
  void shouldApplyIlmToAllHistoricalIndices() throws Exception {
    // given
    retention.setEnabled(true);
    // ensure all templates are created
    startupSchema();
    // create indices for all templates with a date in the index name
    final var searchClientAdapter = new SearchClientAdapter(testClient, objectMapper);
    final String date = "2026-01-10";
    for (final var indexTemplate : resourceProvider.getIndexTemplateDescriptors()) {
      searchClientAdapter.createIndex(indexTemplate.getIndexPattern().replace("*", date), 0);
    }
    final var asyncClient = spy(new ElasticsearchAsyncClient(transport));
    final var indicesClientSpy = spy(asyncClient.indices());
    doReturn(indicesClientSpy).when(asyncClient).indices();
    final var repository = createRepository(asyncClient);
    final var captor = ArgumentCaptor.forClass(PutIndicesSettingsRequest.class);

    // when
    repository.setLifeCycleToAllIndexes();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(
                        indicesClientSpy, times(20) // number of index templates
                        )
                    .putSettings(captor.capture()));

    final var putIndicesSettingsRequests = captor.getAllValues();
    assertThat(putIndicesSettingsRequests)
        .hasSize(20)
        .allSatisfy(
            request -> {
              assertThat(request.index()).hasSize(1);
              final String[] split = request.index().getFirst().split(",");
              assertThat(split)
                  .hasSize(3); // 3 patterns (wildcard + runtime exclusion + alias exclusion)
              final var matchingTemplate =
                  resourceProvider.getIndexTemplateDescriptors().stream()
                      .filter(
                          template ->
                              split[0].matches(template.getAllVersionsIndexNameRegexPattern()))
                      .findFirst();
              assertThat(matchingTemplate).isPresent();
              assertThat(split)
                  .containsExactly(
                      matchingTemplate.get().getIndexPattern(),
                      "-" + matchingTemplate.get().getFullQualifiedName(),
                      "-" + matchingTemplate.get().getAlias());
            });
    for (final var template : resourceProvider.getIndexTemplateDescriptors()) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var settings =
                    testClient
                        .indices()
                        .getSettings(b -> b.index(template.getIndexPattern()))
                        .result();
                // Check runtime index (should not have ILM policy)
                assertThat(
                        settings
                            .get(template.getFullQualifiedName())
                            .settings()
                            .index()
                            .lifecycle())
                    .isNull();
                // Check historical index (should have ILM policy)
                assertThat(
                        settings
                            .get(template.getIndexPattern().replace("*", date))
                            .settings()
                            .index()
                            .lifecycle()
                            .name())
                    .isEqualTo(
                        repository.getRetentionPolicyName(template.getIndexName(), retention));
              });
    }
  }

  private void startupSchema() {
    final var searchEngineClient = new ElasticsearchEngineClient(testClient, objectMapper);
    final var connectConfig = new ConnectConfiguration();
    connectConfig.setIndexPrefix(indexPrefix);
    connectConfig.setType(DatabaseType.ELASTICSEARCH.toString());
    connectConfig.setUrl(SEARCH_DB.esUrl());
    final var schemaManagerConfig = new SchemaManagerConfiguration();
    schemaManagerConfig.getRetry().setMaxRetries(1);
    final SearchEngineConfiguration searchEngineConfiguration =
        SearchEngineConfiguration.of(
            builder ->
                builder
                    .connect(connectConfig)
                    .retention(retention)
                    .schemaManager(schemaManagerConfig));
    new SchemaManager(
            searchEngineClient,
            resourceProvider.getIndexDescriptors(),
            resourceProvider.getIndexTemplateDescriptors(),
            searchEngineConfiguration,
            objectMapper)
        .startup();
  }

  private void putLifecyclePolicies() throws IOException {
    putLifecyclePolicy(retention.getPolicyName(), retention.getMinimumAge());
    putLifecyclePolicy(
        retention.getUsageMetricsPolicyName(), retention.getUsageMetricsMinimumAge());
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

    return createRepository(client);
  }

  private ElasticsearchArchiverRepository createRepository(final int partitionId) {
    final var client = new ElasticsearchAsyncClient(transport);

    return createRepository(client, partitionId);
  }

  private ElasticsearchArchiverRepository createRepository(final ElasticsearchAsyncClient client) {
    return createRepository(client, 1);
  }

  private ElasticsearchArchiverRepository createRepository(
      final ElasticsearchAsyncClient client, final int partitionId) {
    final var metrics = new CamundaExporterMetrics(meterRegistry);

    return new ElasticsearchArchiverRepository(
        partitionId, config, resourceProvider, client, Runnable::run, metrics, LOGGER);
  }

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create(SEARCH_DB.esUrl())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }

  private void createProcessInstanceIndex() throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var endDateProp =
        Property.of(
            p ->
                p.date(
                    d ->
                        d.index(true)
                            .format(
                                "date_time || epoch_millis || strict_date_optional_time || yyyy-MM-dd HH:mm:ss.SSS")));
    final var joinRelationProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var partitionIdProp = Property.of(p -> p.integer(i -> i.index(true)));
    final var rootPIProp = Property.of(p -> p.long_(l -> l.index(true)));
    final var parentPIProp = Property.of(p -> p.long_(l -> l.index(true)));

    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        ListViewTemplate.ID, idProp,
                        ListViewTemplate.END_DATE, endDateProp,
                        ListViewTemplate.JOIN_RELATION, joinRelationProp,
                        ListViewTemplate.PARTITION_ID, partitionIdProp,
                        ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY, rootPIProp,
                        ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY, parentPIProp)));
    testClient
        .indices()
        .create(
            r ->
                r.index(processInstanceIndex)
                    .mappings(properties)
                    .aliases(processInstanceIndex + "alias", a -> a.isWriteIndex(false)));
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
    testClient
        .indices()
        .create(
            r ->
                r.index(batchOperationIndex)
                    .mappings(properties)
                    .aliases(batchOperationIndex + "alias", a -> a.isWriteIndex(false)));
  }

  private void createUsageMetricIndex(final String usageMetricIndex) throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var endTimeProp =
        Property.of(p -> p.date(d -> d.index(true).format("date_time || epoch_millis")));
    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        UsageMetricTemplate.ID,
                        idProp,
                        UsageMetricTemplate.END_TIME,
                        endTimeProp)));
    testClient
        .indices()
        .create(
            r ->
                r.index(usageMetricIndex)
                    .mappings(properties)
                    .aliases(usageMetricIndex + "alias", a -> a.isWriteIndex(false)));
  }

  private void createUsageMetricTUIndex(final String usageMetricTUIndex) throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var endTimeProp =
        Property.of(p -> p.date(d -> d.index(true).format("date_time || epoch_millis")));
    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        UsageMetricTUTemplate.ID,
                        idProp,
                        UsageMetricTUTemplate.END_TIME,
                        endTimeProp)));
    testClient
        .indices()
        .create(
            r ->
                r.index(usageMetricTUIndex)
                    .mappings(properties)
                    .aliases(usageMetricTUIndex + "alias", a -> a.isWriteIndex(false)));
  }

  private void createStandaloneDecisionIndex(final String standaloneDecisionIndex)
      throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var evaluationDateProp =
        Property.of(p -> p.date(d -> d.index(true).format("date_time || epoch_millis")));
    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        DecisionInstanceTemplate.ID,
                        idProp,
                        DecisionInstanceTemplate.EVALUATION_DATE,
                        evaluationDateProp)));
    testClient
        .indices()
        .create(
            r ->
                r.index(standaloneDecisionIndex)
                    .mappings(properties)
                    .aliases(standaloneDecisionIndex + "alias", a -> a.isWriteIndex(false)));
  }

  private void createAuditLogIndex() throws IOException {
    createAuditLogIndex(auditLogIndex);
  }

  private void createAuditLogIndex(final String indexName) throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var entityTypeProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var batchOperationKeyProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        "id",
                        idProp,
                        "entityType",
                        entityTypeProp,
                        "batchOperationKey",
                        batchOperationKeyProp)));
    testClient.indices().create(r -> r.index(indexName).mappings(properties));
  }

  @Test
  void shouldMaintainSeparateArchiverDatesForDifferentTemplates() throws IOException {
    // given
    final var repository = createRepository();
    config.setRolloverInterval("1d");

    final var piEndDate = "2025-05-02T12:00:00.000+0000";
    final var pi =
        new TestProcessInstance(
            String.valueOf(new Random().nextLong()),
            piEndDate,
            ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION,
            1,
            null,
            null);

    final var batchOperationEndDate = "2025-01-02T12:00:00.000+0000";
    final var batchOperation =
        new TestBatchOperation(UUID.randomUUID().toString(), batchOperationEndDate);

    createProcessInstanceIndex();
    createBatchOperationIndex();

    // Create historical indices
    testClient.indices().create(i -> i.index(processInstanceIndex + "_2025-04-30"));
    testClient.indices().create(i -> i.index(batchOperationIndex + "_2024-12-30"));

    index(processInstanceIndex, pi);
    index(batchOperationIndex, batchOperation);
    testClient.indices().refresh(r -> r.index(processInstanceIndex, batchOperationIndex));

    // when
    // ensure PI batch is processed first to assert that both dates are maintained separately
    final var piBatch = repository.getProcessInstancesNextBatch().join();
    final var batchOperationBatch = repository.getBatchOperationsNextBatch().join();

    // then
    assertThat(piBatch.isEmpty()).isFalse();
    assertThat(batchOperationBatch.isEmpty()).isFalse();

    assertThat(piBatch.finishDate()).isEqualTo("2025-05-02");
    assertThat(batchOperationBatch.finishDate()).isEqualTo("2025-01-02");
  }

  private record TestAuditLogDocument(String id, String entityType) implements TDocument {}

  private record TestDocument(String id) implements TDocument {}

  private record TestBatchOperation(String id, String endDate) implements TDocument {}

  private record TestProcessInstance(
      String id,
      String endDate,
      String joinRelation,
      int partitionId,
      Long rootProcessInstanceKey,
      Long parentProcessInstanceKey)
      implements TDocument {}

  private record TestUsageMetric(String id, String endTime, int partitionId) implements TDocument {}

  private record TestUsageMetricTU(String id, String endTime, int partitionId)
      implements TDocument {}

  private record TestStandaloneDecision(
      String id, String evaluationDate, int partitionId, int processInstanceKey)
      implements TDocument {}

  private interface TDocument {
    String id();
  }
}
