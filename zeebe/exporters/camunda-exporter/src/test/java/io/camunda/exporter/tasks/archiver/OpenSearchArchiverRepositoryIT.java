/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static io.camunda.search.test.utils.SearchDBExtension.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
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
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

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
    createLifeCyclePolicy();
    final var result = repository.setIndexLifeCycle(indexName);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));

    // Takes a while for the policy to be applied
    Awaitility.await("until the policy has been visibly applied")
        .untilAsserted(
            () -> assertThat(fetchPolicyForIndex(indexName)).isEqualTo(retention.getPolicyName()));
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

    createLifeCyclePolicy();
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
    assertThat(batch.ids()).containsExactly("1", "2");
    assertThat(batch.finishDate()).isEqualTo(dateFormatter.format(now.minus(Duration.ofHours(2))));
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

  private void createLifeCyclePolicy() {
    final var engineClient = new OpensearchEngineClient(testClient, MAPPER);
    try {
      engineClient.putIndexLifeCyclePolicy(retention.getPolicyName(), retention.getMinimumAge());
    } catch (final Exception e) {
      // policy was already created
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
      final String region = new DefaultAwsRegionProviderChain().getRegion();
      return new OpenSearchClient(
          new AwsSdk2Transport(
              httpClient,
              uri.getHost(),
              Region.of(region),
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
      final String region = new DefaultAwsRegionProviderChain().getRegion();
      return new OpenSearchAsyncClient(
          new AwsSdk2Transport(
              httpClient,
              uri.getHost(),
              Region.of(region),
              AwsSdk2TransportOptions.builder()
                  .setMapper(new JacksonJsonpMapper(new ObjectMapper()))
                  .build()));
    }
  }

  private record TestBatchOperation(String id, String endDate) implements TDocument {}

  private record TestDocument(String id) implements TDocument {}

  private record TestProcessInstance(
      String id, String endDate, String joinRelation, int partitionId) implements TDocument {}

  private record DeleteRequest(String endpoint) implements Request {

    @Override
    public String getMethod() {
      return "DELETE";
    }

    @Override
    public String getEndpoint() {
      return endpoint;
    }

    @Override
    public Map<String, String> getParameters() {
      return Map.of();
    }

    @Override
    public Collection<Entry<String, String>> getHeaders() {
      return List.of();
    }

    @Override
    public Optional<Body> getBody() {
      return Optional.empty();
    }
  }

  private interface TDocument {
    String id();
  }
}
