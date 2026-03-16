/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import static io.camunda.search.test.utils.SearchDBExtension.HISTORY_DELETION_ID_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class HistoryDeletionRepositoryIT {

  public static final int PARTITION_ID = 1;
  @RegisterExtension protected static SearchDBExtension searchDB = create();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  protected final HistoryDeletionIndex historyDeletionIndex;
  protected final AuditLogCleanupIndex auditLogCleanupIndex;
  protected final OperationTemplate operationTemplate;
  @AutoClose protected final ClientAdapter clientAdapter;
  protected final SearchEngineClient engineClient;
  protected final ExporterConfiguration config;
  private HistoryDeletionRepository repository;
  private final String indexPrefix;

  public HistoryDeletionRepositoryIT(final String databaseUrl, final boolean isElastic) {
    config = new ExporterConfiguration();
    indexPrefix = HISTORY_DELETION_ID_PREFIX + UUID.randomUUID();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(databaseUrl);
    config.getConnect().setType(isElastic ? "elasticsearch" : "opensearch");
    config.getConnect().setAwsEnabled(searchDB.isAws());

    clientAdapter = ClientAdapter.of(config.getConnect());
    engineClient = clientAdapter.getSearchEngineClient();

    historyDeletionIndex = new HistoryDeletionIndex(indexPrefix, isElastic);
    auditLogCleanupIndex = new AuditLogCleanupIndex(indexPrefix, isElastic);
    operationTemplate = new OperationTemplate(indexPrefix, isElastic);
  }

  @BeforeEach
  void beforeEach() {
    engineClient.createIndex(historyDeletionIndex, new IndexConfiguration());
    engineClient.createIndex(auditLogCleanupIndex, new IndexConfiguration());
    engineClient.createIndex(operationTemplate, new IndexConfiguration());
    repository = createRepository(indexPrefix, PARTITION_ID);
  }

  @AfterEach
  void afterEach() {
    engineClient.deleteIndex(historyDeletionIndex.getFullQualifiedName());
    engineClient.createIndex(historyDeletionIndex, new IndexConfiguration());
    engineClient.deleteIndex(auditLogCleanupIndex.getFullQualifiedName());
    engineClient.createIndex(auditLogCleanupIndex, new IndexConfiguration());
    engineClient.deleteIndex(operationTemplate.getFullQualifiedName());
    engineClient.createIndex(operationTemplate, new IndexConfiguration());
  }

  protected abstract HistoryDeletionRepository createRepository(
      final String indexPrefix, final int partitionId);

  private HistoryDeletionEntity createHistoryDeletionEntity() throws IOException {
    return createHistoryDeletionEntity(PARTITION_ID);
  }

  private HistoryDeletionEntity createHistoryDeletionEntity(final int partitionId)
      throws IOException {
    final var entity = new HistoryDeletionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setPartitionId(partitionId);
    entity.setResourceKey(ThreadLocalRandom.current().nextLong());
    entity.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);
    index(entity);
    return entity;
  }

  protected abstract void index(final HistoryDeletionEntity entity) throws IOException;

  protected abstract long countAuditLogCleanupEntries() throws IOException;

  @Test
  void shouldGetEmptyListWhenNoEntitiesToDelete() {
    // given - no entities

    // when
    final var future = repository.getNextBatch();

    // then
    assertThat(future)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(HistoryDeletionBatch::historyDeletionEntities)
        .asInstanceOf(InstanceOfAssertFactories.list(HistoryDeletionEntity.class))
        .isEmpty();
  }

  @Test
  void shouldGetEntitiesToDelete() throws IOException {
    // given
    final var entity = createHistoryDeletionEntity();

    // when
    final var future = repository.getNextBatch();

    // then
    assertThat(future)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(HistoryDeletionBatch::historyDeletionEntities)
        .asInstanceOf(InstanceOfAssertFactories.list(HistoryDeletionEntity.class))
        .extracting(HistoryDeletionEntity::getId, HistoryDeletionEntity::getResourceType)
        .containsOnly(tuple(entity.getId(), HistoryDeletionType.PROCESS_INSTANCE));
  }

  @Test
  void shouldNotFindEntitiesForOtherPartitions() throws IOException {
    // given
    createHistoryDeletionEntity(2);

    // when
    final var future = repository.getNextBatch();

    // then
    assertThat(future)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(HistoryDeletionBatch::historyDeletionEntities)
        .asInstanceOf(InstanceOfAssertFactories.list(HistoryDeletionEntity.class))
        .isEmpty();
  }

  @Test
  void shouldDeleteDocumentsByFieldValue() throws IOException {
    // given
    final var entity1 = createHistoryDeletionEntity();
    final var entity2 = createHistoryDeletionEntity();

    // when
    repository.deleteDocumentsByField(
        historyDeletionIndex.getFullQualifiedName(),
        HistoryDeletionIndex.RESOURCE_KEY,
        List.of(entity1.getResourceKey(), entity2.getResourceKey()));

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(repository.getNextBatch())
                    .succeedsWithin(REQUEST_TIMEOUT)
                    .extracting(HistoryDeletionBatch::historyDeletionEntities)
                    .asInstanceOf(InstanceOfAssertFactories.list(HistoryDeletionEntity.class))
                    .isEmpty());
  }

  @Test
  void shouldDeleteDocumentsById() throws IOException {
    // given
    final var entity1 = createHistoryDeletionEntity();
    final var entity2 = createHistoryDeletionEntity();

    // when
    repository.deleteDocumentsById(
        historyDeletionIndex.getFullQualifiedName(), List.of(entity1.getId(), entity2.getId()));

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(repository.getNextBatch())
                    .succeedsWithin(REQUEST_TIMEOUT)
                    .extracting(HistoryDeletionBatch::historyDeletionEntities)
                    .asInstanceOf(InstanceOfAssertFactories.list(HistoryDeletionEntity.class))
                    .isEmpty());
  }

  @Test
  void shouldCreateAuditLogCleanupEntries() throws IOException {
    // given
    final var entity1 = createHistoryDeletionEntity();
    final var entity2 = createHistoryDeletionEntity();
    final var deletedResourceIds = Set.of(entity1.getId(), entity2.getId());

    // when
    repository
        .createAuditLogCleanupEntries(List.of(entity1, entity2), deletedResourceIds)
        .toCompletableFuture()
        .join();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final long auditLogCount = countAuditLogCleanupEntries();
              assertThat(auditLogCount).isEqualTo(2);
            });
  }

  @Test
  void shouldIgnoreDocumentMissingExceptionWhenCompletingNonExistentOperations() {
    // given - non-existent operation IDs that will trigger document_missing_exception
    final var nonExistentIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    // when
    final var result = repository.completeOperations(nonExistentIds);

    // then - should complete successfully
    assertThat(result).succeedsWithin(REQUEST_TIMEOUT);
    assertThat(result.join()).containsExactlyElementsOf(nonExistentIds);
  }
}
