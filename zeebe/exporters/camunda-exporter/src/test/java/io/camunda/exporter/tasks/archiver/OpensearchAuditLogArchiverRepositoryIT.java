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
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
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

final class OpensearchAuditLogArchiverRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchAuditLogArchiverRepositoryIT.class);

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();
  private static final ObjectMapper MAPPER = TestObjectMapper.objectMapper();

  @AutoClose private final OpenSearchTransport transport = createTransport();
  private final HistoryConfiguration config = new HistoryConfiguration();
  private String auditLogIndex;
  private String auditLogCleanupIndex;
  private TestExporterResourceProvider resourceProvider;
  private final OpenSearchClient testClient = createOpenSearchClient();
  private final Clock clock = Clock.fixed(Instant.parse("2026-02-19T10:00:00Z"), ZoneOffset.UTC);
  private String indexPrefix;
  private String zeebeIndex;

  @AfterEach
  void afterEach() throws IOException {
    deleteTestIndices();
    deleteAllTestPolicies();
  }

  @BeforeEach
  void beforeEach() {
    indexPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    zeebeIndex = indexPrefix + "-" + UUID.randomUUID();
    resourceProvider = new TestExporterResourceProvider(indexPrefix, true);
    auditLogIndex =
        resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class).getFullQualifiedName();
    auditLogCleanupIndex =
        resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class).getFullQualifiedName();
  }

  @Test
  void shouldReturnEmptyBatchWhenNoCleanupEntities() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository();

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds()).isEmpty();
    assertThat(batch.auditLogIds()).isEmpty();
    assertThat(batch.finishDate()).isNull();
    assertThat(batch.isEmpty()).isTrue();
  }

  @Test
  void shouldReturnCleanupBatchWithNoAuditLogsWhenNoMatchingAuditLogs() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository();

    final var cleanupEntity =
        new AuditLogCleanupEntity()
            .setKey("123")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
            .setPartitionId(1);

    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity);
    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds()).containsExactly("cleanup-1");
    assertThat(batch.auditLogIds()).isEmpty();
    assertThat(batch.finishDate()).isEqualTo("2026-02-19");
    assertThat(batch.size()).isEqualTo(1);
  }

  @Test
  void shouldReturnBatchWithMatchingAuditLogsByKeyField() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository();

    final var cleanupEntity =
        new AuditLogCleanupEntity()
            .setKey("123")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
            .setPartitionId(1);
    final var auditLog1 = Map.of("processInstanceKey", "123", "entityType", "PROCESS_INSTANCE");
    final var auditLog2 = Map.of("processInstanceKey", "456", "entityType", "PROCESS_INSTANCE");
    final var auditLog3 = Map.of("processInstanceKey", "123", "entityType", "USER_TASK");

    index(auditLogIndex, "audit-1", auditLog1);
    index(auditLogIndex, "audit-2", auditLog2);
    index(auditLogIndex, "audit-3", auditLog3);
    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity);

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex, auditLogIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds()).containsExactly("cleanup-1");
    assertThat(batch.auditLogIds())
        .as("Only audit-1 matches both key and entityType")
        .containsExactly("audit-1");
  }

  @Test
  void shouldReturnBatchWithMultipleCleanupEntities() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository();

    final var cleanupEntity1 =
        new AuditLogCleanupEntity()
            .setKey("123")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
            .setPartitionId(1);
    final var cleanupEntity2 =
        new AuditLogCleanupEntity()
            .setKey("456")
            .setKeyField(AuditLogTemplate.PROCESS_DEFINITION_KEY)
            .setEntityType(AuditLogEntityType.USER)
            .setPartitionId(1);
    final var auditLog1 = Map.of("processInstanceKey", "123", "entityType", "PROCESS_INSTANCE");
    final var auditLog2 = Map.of("processDefinitionKey", "456", "entityType", "USER");

    index(auditLogIndex, "audit-1", auditLog1);
    index(auditLogIndex, "audit-2", auditLog2);
    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity1);
    index(auditLogCleanupIndex, "cleanup-2", cleanupEntity2);

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex, auditLogIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds()).containsExactlyInAnyOrder("cleanup-1", "cleanup-2");
    assertThat(batch.auditLogIds()).containsExactlyInAnyOrder("audit-1", "audit-2");
    assertThat(batch.size()).isEqualTo(4);
  }

  @Test
  void shouldReturnBatchForCleanupEntityWithoutEntityType() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository();

    final var cleanupEntity =
        new AuditLogCleanupEntity()
            .setKey("789")
            .setKeyField(AuditLogTemplate.BATCH_OPERATION_KEY)
            .setPartitionId(1);
    final var auditLog1 = Map.of("batchOperationKey", "789", "entityType", "PROCESS_INSTANCE");
    final var auditLog2 = Map.of("batchOperationKey", "789", "entityType", "USER_TASK");
    final var auditLog3 = Map.of("batchOperationKey", "999", "entityType", "USER_TASK");

    index(auditLogIndex, "audit-1", auditLog1);
    index(auditLogIndex, "audit-2", auditLog2);
    index(auditLogIndex, "audit-3", auditLog3);
    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity);

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex, auditLogIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds()).containsExactly("cleanup-1");
    assertThat(batch.auditLogIds())
        .as("Should match all audit logs with batchOperationKey=789 regardless of entityType")
        .containsExactlyInAnyOrder("audit-1", "audit-2");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void shouldFilterCleanupEntitiesByPartitionId(final int partitionId) throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository(partitionId);

    final var cleanupEntity1 =
        new AuditLogCleanupEntity()
            .setKey("111")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
            .setPartitionId(1);
    final var cleanupEntity2 =
        new AuditLogCleanupEntity()
            .setKey("222")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
            .setPartitionId(2);
    final var auditLog1 = Map.of("processInstanceKey", "111", "entityType", "PROCESS_INSTANCE");
    final var auditLog2 = Map.of("processInstanceKey", "222", "entityType", "PROCESS_INSTANCE");

    index(auditLogIndex, "audit-1", auditLog1);
    index(auditLogIndex, "audit-2", auditLog2);
    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity1);
    index(auditLogCleanupIndex, "cleanup-2", cleanupEntity2);

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex, auditLogIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();

    if (partitionId == 1) {
      assertThat(batch.auditLogCleanupIds()).containsExactly("cleanup-1");
      assertThat(batch.auditLogIds()).containsExactly("audit-1");
    } else {
      assertThat(batch.auditLogCleanupIds()).containsExactly("cleanup-2");
      assertThat(batch.auditLogIds()).containsExactly("audit-2");
    }
  }

  @Test
  void shouldRespectBatchSize() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    config.setRolloverBatchSize(2);
    final var repository = createRepository();

    // Create 3 cleanup entities
    for (int i = 1; i <= 3; i++) {
      final var cleanupEntity =
          new AuditLogCleanupEntity()
              .setKey(String.valueOf(i))
              .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
              .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
              .setPartitionId(1);
      index(auditLogCleanupIndex, "cleanup-" + i, cleanupEntity);
    }

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex, auditLogIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds())
        .as("Should only return 2 cleanup entities due to batch size limit")
        .hasSize(2);
  }

  @Test
  void shouldDeleteAuditLogCleanupMetadata() throws IOException {
    // given
    createAuditLogCleanupIndex();
    final var repository = createRepository();

    final var cleanupEntity1 =
        new AuditLogCleanupEntity()
            .setKey("123")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setPartitionId(1);
    final var cleanupEntity2 =
        new AuditLogCleanupEntity()
            .setKey("456")
            .setKeyField(AuditLogTemplate.BATCH_OPERATION_KEY)
            .setPartitionId(1);

    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity1);
    index(auditLogCleanupIndex, "cleanup-2", cleanupEntity2);
    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex));

    final var batch =
        new ArchiveBatch.AuditLogCleanupBatch(
            "2026-02-19", List.of("cleanup-1", "cleanup-2"), List.of());

    // when
    final var result = repository.deleteAuditLogCleanupMetadata(batch);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    assertThat(result.join()).isEqualTo(2);

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex));
    final var remaining =
        testClient.search(
            r -> r.index(auditLogCleanupIndex).requestCache(false), AuditLogCleanupEntity.class);
    assertThat(remaining.hits().hits()).isEmpty();
  }

  @Test
  void shouldHandleMixedCleanupEntitiesWithAndWithoutEntityType() throws IOException {
    // given
    createAuditLogCleanupIndex();
    createAuditLogIndex();
    final var repository = createRepository();

    final var cleanupEntity1 =
        new AuditLogCleanupEntity()
            .setKey("123")
            .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
            .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
            .setPartitionId(1);
    final var cleanupEntity2 =
        new AuditLogCleanupEntity()
            .setKey("456")
            .setKeyField(AuditLogTemplate.BATCH_OPERATION_KEY)
            .setPartitionId(1);
    final var auditLog1 = Map.of("processInstanceKey", "123", "entityType", "PROCESS_INSTANCE");
    final var auditLog2 = Map.of("processInstanceKey", "123", "entityType", "USER_TASK");
    final var auditLog3 = Map.of("batchOperationKey", "456", "entityType", "BATCH");
    final var auditLog4 = Map.of("batchOperationKey", "456", "entityType", "USER_TASK");

    index(auditLogIndex, "audit-1", auditLog1);
    index(auditLogIndex, "audit-2", auditLog2);
    index(auditLogIndex, "audit-3", auditLog3);
    index(auditLogIndex, "audit-4", auditLog4);
    index(auditLogCleanupIndex, "cleanup-1", cleanupEntity1);
    index(auditLogCleanupIndex, "cleanup-2", cleanupEntity2);

    testClient.indices().refresh(r -> r.index(auditLogCleanupIndex, auditLogIndex));

    // when
    final var result = repository.getNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    final var batch = result.join();
    assertThat(batch.auditLogCleanupIds()).containsExactlyInAnyOrder("cleanup-1", "cleanup-2");
    assertThat(batch.auditLogIds())
        .as(
            "audit-1 matches cleanup-1 (key+entityType), audit-3 and audit-4 match cleanup-2 (key only)")
        .containsExactlyInAnyOrder("audit-1", "audit-3", "audit-4");
  }

  private OpensearchAuditLogArchiverRepository createRepository() {
    return createRepository(1);
  }

  private OpensearchAuditLogArchiverRepository createRepository(final int partitionId) {
    final var client = new OpenSearchAsyncClient(transport);
    final var auditLogCleanupDescriptor =
        resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);
    final var auditLogTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

    return new OpensearchAuditLogArchiverRepository(
        partitionId,
        client,
        Runnable::run,
        LOGGER,
        auditLogCleanupDescriptor,
        auditLogTemplateDescriptor,
        config,
        clock);
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

  private void createAuditLogCleanupIndex() throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var keyProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var keyFieldProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var entityTypeProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var partitionIdProp = Property.of(p -> p.integer(i -> i.index(true)));

    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        AuditLogCleanupIndex.ID,
                        idProp,
                        AuditLogCleanupIndex.KEY,
                        keyProp,
                        AuditLogCleanupIndex.KEY_FIELD,
                        keyFieldProp,
                        AuditLogCleanupIndex.TYPE_FIELD,
                        entityTypeProp,
                        AuditLogCleanupIndex.PARTITION_ID,
                        partitionIdProp)));
    testClient.indices().create(r -> r.index(auditLogCleanupIndex).mappings(properties));
  }

  private void createAuditLogIndex() throws IOException {
    final var idProp = Property.of(p -> p.keyword(k -> k.index(true)));
    final var keywordProp = Property.of(p -> p.keyword(k -> k.index(true)));

    final var properties =
        TypeMapping.of(
            m ->
                m.properties(
                    Map.of(
                        AuditLogTemplate.ID,
                        idProp,
                        AuditLogTemplate.ENTITY_TYPE,
                        keywordProp,
                        AuditLogTemplate.PROCESS_INSTANCE_KEY,
                        keywordProp,
                        AuditLogTemplate.BATCH_OPERATION_KEY,
                        keywordProp)));
    testClient.indices().create(r -> r.index(auditLogIndex).mappings(properties));
  }

  private <T> void index(final String index, final String id, final T document) {
    try {
      testClient.index(b -> b.index(index).document(document).id(id));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
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
                  .setMapper(
                      new org.opensearch.client.json.jackson.JacksonJsonpMapper(new ObjectMapper()))
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
                      g.index(indexPrefix + "*", zeebeIndex + "*", ARCHIVER_IDX_PREFIX + "*")
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
}
