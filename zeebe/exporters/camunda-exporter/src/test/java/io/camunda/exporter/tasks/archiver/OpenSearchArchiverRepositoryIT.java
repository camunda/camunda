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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
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
import java.net.URI;
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
import org.apache.hc.core5.http.HttpHost;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

@SuppressWarnings("resource")
final class OpenSearchArchiverRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchArchiverRepositoryIT.class);
  @RegisterExtension private static final SearchDBExtension SEARCH_DB = create();
  private static final ObjectMapper MAPPER = TestObjectMapper.objectMapper();
  @AutoClose private final OpenSearchTransport transport = createTransport();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoryConfiguration config = new HistoryConfiguration();
  private final ConnectConfiguration connectConfiguration = new ConnectConfiguration();
  private final RetentionConfiguration retention = new RetentionConfiguration();
  private String processInstanceIndex;
  private String batchOperationIndex;
  private String auditLogIndex;
  private final OpenSearchClient testClient = createOpenSearchClient();
  private final String zeebeIndexPrefix = "zeebe-record";
  private final String zeebeIndex = zeebeIndexPrefix + "-" + UUID.randomUUID();
  private TestExporterResourceProvider resourceProvider;
  private String indexPrefix;
  private final ObjectMapper objectMapper = TestObjectMapper.objectMapper();

  @AfterEach
  void afterEach() {
    deleteTestIndices();
    deleteAllTestPolicies();
  }

  @BeforeEach
  void beforeEach() {
    config.setRetention(retention);
    indexPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    resourceProvider = new TestExporterResourceProvider(indexPrefix, false);
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
    final var indexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
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
    final var sourceIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
    final var destIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
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
    final var sourceIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
    final var destIndexName = ARCHIVER_IDX_PREFIX + UUID.randomUUID().toString();
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
        .first()
        .extracting(h -> h.source().id())
        .isEqualTo("2");

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

    retention.setEnabled(true);

    // when
    createLifeCyclePolicies();
    final var result = repository.setIndexLifeCycle(taskIndex);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    assertThat(fetchPolicyForIndexWithAwait(taskIndex))
        .as(
            "Expected '%s' policy to be applied for index: '%s'",
            retention.getPolicyName(), taskIndex)
        .isNotNull()
        .isEqualTo(retention.getPolicyName());
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

    retention.setEnabled(true);

    // when
    createLifeCyclePolicies();
    final var result = repository.setIndexLifeCycle(usageMetricIndex);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    assertThat(fetchPolicyForIndexWithAwait(usageMetricIndex))
        .as(
            "Expected '%s' policy to be applied for index: '%s'",
            retention.getUsageMetricsPolicyName(), usageMetricIndex)
        .isNotNull()
        .isEqualTo(retention.getUsageMetricsPolicyName());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policy modification not allowed")
  void shouldSetIndexLifeCycleOnAllValidIndexes(final boolean withPrefix) throws IOException {
    // given
    final var prefix = withPrefix ? indexPrefix : "";
    resourceProvider = new TestExporterResourceProvider(prefix, true);
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

    connectConfiguration.setIndexPrefix(prefix);
    final var repository = createRepository();
    final var indices = new ArrayList<>(historicalIndices);
    indices.addAll(usageMetricsIndices);
    indices.addAll(untouchedIndices);

    retention.setEnabled(true);
    retention.setPolicyName("default-policy");
    retention.setUsageMetricsPolicyName("custom-usage-metrics-policy");

    createLifeCyclePolicies();
    for (final var index : indices) {
      testClient.indices().create(r -> r.index(index));
    }

    // when
    final var result = repository.setLifeCycleToAllIndexes();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    // verify that the usage metrics policy was applied to all usage metric indices
    for (final var index : usageMetricsIndices) {
      assertThat(fetchPolicyForIndexWithAwait(index))
          .as("Expected 'custom-usage-metrics-policy' policy to be applied for %s", index)
          .isNotNull()
          .isEqualTo("custom-usage-metrics-policy");
    }
    // verify that the default policy was applied to all other indices
    for (final var index : historicalIndices) {
      assertThat(fetchPolicyForIndexWithAwait(index))
          .as("Expected 'default-policy' policy to be applied for %s", index)
          .isNotNull()
          .isEqualTo("default-policy");
    }

    for (final var index : untouchedIndices) {
      assertThat(fetchPolicyForIndex(index)).as("no policy applied to %s", index).isEqualTo("null");
    }
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
        .containsExactly("3");
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
      final String id, final String endDate, final Long rootPI, final Long parentPI)
      throws IOException {
    final var doc =
        new TestProcessInstance(
            id, endDate, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION, 1, rootPI, parentPI);
    index(processInstanceIndex, doc);
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
    assertThat(batch.ids()).containsExactlyInAnyOrder("1", "2");
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
              assertThat(firstBatch.get().ids()).containsExactlyInAnyOrder("1", "2");
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
              assertThat(secondBatch.get().ids()).containsExactlyInAnyOrder("3", "4");
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
              assertThat(thirdBatch.get().ids()).containsExactlyInAnyOrder("5", "6");
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
    assertThat(batch.ids()).containsExactlyInAnyOrder("1", "2");
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
    assertThat(batch.ids()).containsExactlyInAnyOrder("1", "2");
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofDays(1))));
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
            new TestUsageMetric("4", twoHoursAgo, 21),
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
      assertThat(batch.ids()).containsExactlyInAnyOrder("1", "2", "3");
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
      assertThat(batch.ids()).containsExactlyInAnyOrder("10", "11", "12");
    } else {
      assertThat(batch.ids()).containsExactly("21");
    }
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
  }

  @Test
  void shouldCacheIndicesWhichHaveRetentionPolicyAppliedAndNotReapplyPointlessly() {
    // given
    retention.setEnabled(true);
    final var indexName1 = processInstanceIndex + UUID.randomUUID();
    final var indexName2 = processInstanceIndex + UUID.randomUUID();

    final var asyncClient = createOpenSearchAsyncClient();
    final var genericClientSpy =
        Mockito.spy(
            new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions()));

    final var repository = createRepository(genericClientSpy);

    // when - first time setting policy for indexName2 it should make the ism add for
    // indexName2
    repository.setIndexLifeCycle(indexName2);

    final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    Awaitility.await()
        .untilAsserted(() -> verify(genericClientSpy, times(1)).executeAsync(captor.capture()));

    final var request = captor.getValue();
    assertThat(request.getEndpoint()).isEqualTo("/_plugins/_ism/add/" + indexName2);
    reset(genericClientSpy);

    // setting policy first time for srcIndexName but second time for indexName2, it
    // should have cached the fact that indexName2 already has a ism and not be included in
    // the requests.
    repository.setIndexLifeCycle(indexName2);
    repository.setIndexLifeCycle(indexName1);

    // then
    final var captor2 = ArgumentCaptor.forClass(Request.class);

    Awaitility.await()
        .untilAsserted(() -> verify(genericClientSpy, times(1)).executeAsync(captor2.capture()));

    final Request request2 = captor2.getValue();

    assertThat(request2.getEndpoint()).isEqualTo("/_plugins/_ism/add/" + indexName1);
  }

  @Test
  void shouldReapplyILMPolicyAfterRetentionPeriodExpiration() throws Exception {
    // given
    retention.setEnabled(true);
    final int minimumAgeSeconds = 2;
    retention.setMinimumAge("%ds".formatted(minimumAgeSeconds));
    final var indexName1 = processInstanceIndex + UUID.randomUUID();
    final var indexName2 = processInstanceIndex + UUID.randomUUID();

    final var asyncClient = createOpenSearchAsyncClient();
    final var genericClientSpy =
        Mockito.spy(
            new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions()));

    final var repository = createRepository(genericClientSpy);

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

    final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    verify(genericClientSpy, times(2)).executeAsync(captor.capture());

    final var requests = captor.getAllValues();
    assertThat(requests)
        .map(Request::getEndpoint)
        .containsExactlyInAnyOrder(
            "/_plugins/_ism/add/" + indexName1, "/_plugins/_ism/add/" + indexName2);

    // we reset the spy to ensure that we only capture the next calls
    reset(genericClientSpy);

    // setting policy second time for indexName1 and indexName2
    repository.setIndexLifeCycle(indexName1).get();
    repository.setIndexLifeCycle(indexName2).get();

    // then - only indexName1 should be included in the request, since the cache for it has expired
    final var captor2 = ArgumentCaptor.forClass(Request.class);
    verify(genericClientSpy, times(1)).executeAsync(captor2.capture());
    final var request2 = captor2.getValue();
    assertThat(request2.getEndpoint()).isEqualTo("/_plugins/_ism/add/" + indexName1);
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
    final var asyncClient = createOpenSearchAsyncClient();
    final var genericClientSpy =
        Mockito.spy(
            new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions()));
    final var repository = createRepository(genericClientSpy);
    final var captor = ArgumentCaptor.forClass(Request.class);

    // when
    repository.setLifeCycleToAllIndexes();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(
                        genericClientSpy, times(20) // number of index templates
                        )
                    .executeAsync(captor.capture()));

    final var putIndicesSettingsRequests = captor.getAllValues();
    assertThat(putIndicesSettingsRequests)
        .hasSize(20)
        .allSatisfy(
            request -> {
              final var indexPattern =
                  request.getEndpoint().substring("/_plugins/_ism/add/".length());
              final String[] split = indexPattern.split(",");
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
                final var response =
                    genericClientSpy
                        .executeAsync(
                            Requests.builder()
                                .method("GET")
                                .endpoint("_plugins/_ism/explain/" + template.getIndexPattern())
                                .build())
                        .get();

                final var json =
                    objectMapper.readTree(response.getBody().orElseThrow().bodyAsString());
                // Check runtime index (should not have ISM policy)
                assertThat(
                        json.get(template.getFullQualifiedName())
                            .get("index.plugins.index_state_management.policy_id")
                            .isNull())
                    .isTrue();
                // Check dated index (should have ISM policy)
                assertThat(
                        json.get(template.getIndexPattern().replace("*", date))
                            .get("index.plugins.index_state_management.policy_id")
                            .asText())
                    .isEqualTo(
                        repository.getRetentionPolicyName(template.getIndexName(), retention));
              });
    }
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

  private void startupSchema() {
    final var searchEngineClient = new OpensearchEngineClient(testClient, objectMapper);
    final var connectConfig = new ConnectConfiguration();
    connectConfig.setIndexPrefix(indexPrefix);
    connectConfig.setUrl(SEARCH_DB.esUrl());
    connectConfig.setType(DatabaseType.OPENSEARCH.toString());
    final var schemaManagerConfig = new SchemaManagerConfiguration();
    schemaManagerConfig.getRetry().setMaxRetries(1);
    final var searchEngineConfiguration =
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

  private <T extends TDocument> void index(final String index, final T document) {
    try {
      testClient.index(b -> b.index(index).document(document).id(document.id()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void createLifeCyclePolicies() {
    createLifeCyclePolicy(retention.getPolicyName(), retention.getMinimumAge());
    createLifeCyclePolicy(
        retention.getUsageMetricsPolicyName(), retention.getUsageMetricsMinimumAge());
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
    final var request =
        Requests.builder().method("GET").endpoint("/_plugins/_ism/explain/" + indexName).build();
    try {
      final var response = testClient.generic().execute(request);
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
    // In OS, it takes a little while for the policy to be visibly applied, and flushing seems to
    // have no effect on that
    return Awaitility.await("until the policy has been visibly applied")
        .until(() -> fetchPolicyForIndex(indexName), policy -> !policy.equals("null"));
  }

  // no need to close resource returned here, since the transport is closed above anyway
  private OpenSearchArchiverRepository createRepository() {
    return createRepository(1);
  }

  private OpenSearchArchiverRepository createRepository(final int partitionId) {
    final var client = createOpenSearchAsyncClient();

    return createRepository(
        new OpenSearchGenericClient(client._transport(), client._transportOptions()), partitionId);
  }

  private OpenSearchArchiverRepository createRepository(
      final OpenSearchGenericClient genericClient) {
    return createRepository(genericClient, 1);
  }

  private OpenSearchArchiverRepository createRepository(
      final OpenSearchGenericClient genericClient, final int partitionId) {
    final var client = createOpenSearchAsyncClient();
    final var metrics = new CamundaExporterMetrics(meterRegistry);

    return new OpenSearchArchiverRepository(
        partitionId,
        config,
        resourceProvider,
        client,
        genericClient,
        Runnable::run,
        metrics,
        LOGGER);
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

  private OpenSearchTransport createTransport() {
    try {
      return ApacheHttpClient5TransportBuilder.builder(HttpHost.create(SEARCH_DB.osUrl()))
          .setMapper(new JacksonJsonpMapper())
          .build();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
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

  private void deleteTestIndices() {
    // Delete only indices that were created by this test class. Criteria:
    //  - start with dynamic indexPrefix (runtime & historical indices created via templates)
    //  - start with zeebeIndexPrefix (simulated existing zeebe indices used in tests)
    //  - start with ARCHIVER_IDX_PREFIX (ad‑hoc indices for generic operations tests)
    final var indicesToDelete = new ArrayList<String>();
    try {
      indicesToDelete.addAll(
          testClient
              .indices()
              .get(
                  g ->
                      g.index(indexPrefix + "*", zeebeIndexPrefix + "*", ARCHIVER_IDX_PREFIX + "*")
                          .ignoreUnavailable(true)
                          .allowNoIndices(true))
              .result()
              .keySet());
    } catch (final Exception e) {
      LOGGER.error("Error during retrieving indices", e);
    }

    if (indicesToDelete.isEmpty()) {
      LOGGER.debug("No indices found to delete");
      return;
    }

    for (final var index : indicesToDelete) {
      try {
        testClient.indices().delete(d -> d.index(index).ignoreUnavailable(true));
        LOGGER.debug("Deleted test index: {}", index);
      } catch (final Exception e) {
        LOGGER.warn("Failed to delete index {}: {}", index, e.getMessage());
      }
    }
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

  private record TestAuditLogDocument(String id, String entityType) implements TDocument {}

  private record TestBatchOperation(String id, String endDate) implements TDocument {}

  private record TestDocument(String id) implements TDocument {}

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
