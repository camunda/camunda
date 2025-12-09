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

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class HistoryDeletionRepositoryIT {

  public static final int PARTITION_ID = 1;
  @RegisterExtension protected static SearchDBExtension searchDB = create();
  private static final Logger LOGGER = LoggerFactory.getLogger(HistoryDeletionRepositoryIT.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  protected final HistoryDeletionIndex historyDeletionIndex;
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
  }

  @BeforeEach
  void beforeEach() {
    engineClient.createIndex(historyDeletionIndex, new IndexConfiguration());
    repository = createRepository(indexPrefix, PARTITION_ID);
  }

  @AfterEach
  void afterEach() {
    engineClient.deleteIndex(historyDeletionIndex.getFullQualifiedName());
    engineClient.createIndex(historyDeletionIndex, new IndexConfiguration());
  }

  protected abstract HistoryDeletionRepository createRepository(
      final String indexPrefix, final int partitionId);

  private void createHistoryDeletionEntity(final String id) throws IOException {
    createHistoryDeletionEntity(id, PARTITION_ID);
  }

  private void createHistoryDeletionEntity(final String id, final int partitionId)
      throws IOException {
    final var entity = new HistoryDeletionEntity();
    entity.setId(id);
    entity.setPartitionId(partitionId);
    entity.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);
    index(entity);
  }

  protected abstract void index(final HistoryDeletionEntity entity) throws IOException;

  @Test
  void shouldGetEmptyListWhenNoEntitiesToDelete() {
    // given - no entities

    // when
    final var future = repository.getNextBatch();

    // then
    assertThat(future)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(HistoryDeletionBatch::ids)
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .isEmpty();
  }

  @Test
  void shouldGetEntitiesToDelete() throws IOException {
    // given
    final var entityId = UUID.randomUUID().toString();
    createHistoryDeletionEntity(entityId);

    // when
    final var future = repository.getNextBatch();

    // then
    assertThat(future)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(HistoryDeletionBatch::ids)
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .hasSize(1)
        .containsEntry(entityId, HistoryDeletionType.PROCESS_INSTANCE);
  }

  @Test
  void shouldNotFindEntitiesForOtherPartitions() throws IOException {
    // given
    final var entityId = UUID.randomUUID().toString();
    createHistoryDeletionEntity(entityId, 2);

    // when
    final var future = repository.getNextBatch();

    // then
    assertThat(future)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(HistoryDeletionBatch::ids)
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .isEmpty();
  }
}
