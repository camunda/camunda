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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpHost;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
final class ElasticsearchAuditLogArchiverRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchAuditLogArchiverRepositoryIT.class);

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @AutoClose private final RestClientTransport transport = createRestClient();
  private final HistoryConfiguration config = new HistoryConfiguration();
  private String auditLogIndex;
  private String auditLogCleanupIndex;
  private TestExporterResourceProvider resourceProvider;
  private final ElasticsearchClient testClient = new ElasticsearchClient(transport);
  private final Clock clock = Clock.fixed(Instant.parse("2026-02-19T10:00:00Z"), ZoneOffset.UTC);

  @AfterEach
  void afterEach() throws IOException {
    // wipes all data in ES between tests
    final var response = transport.restClient().performRequest(new Request("DELETE", "_all"));
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
  }

  @BeforeEach
  void beforeEach() {
    final var indexPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
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

  private ElasticsearchAuditLogArchiverRepository createRepository() {
    return createRepository(1);
  }

  private ElasticsearchAuditLogArchiverRepository createRepository(final int partitionId) {
    final var client = new ElasticsearchAsyncClient(transport);
    final var auditLogCleanupDescriptor =
        resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);
    final var auditLogTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

    return new ElasticsearchAuditLogArchiverRepository(
        partitionId,
        client,
        Runnable::run,
        LOGGER,
        auditLogCleanupDescriptor,
        auditLogTemplateDescriptor,
        config,
        clock);
  }

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create(SEARCH_DB.esUrl())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
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
}
