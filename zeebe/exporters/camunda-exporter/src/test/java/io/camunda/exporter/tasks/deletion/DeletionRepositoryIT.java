/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import static io.camunda.search.test.utils.SearchDBExtension.create;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.DeleterConfiguration;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class DeletionRepositoryIT {
  @RegisterExtension protected static SearchDBExtension searchDB = create();
  protected static final int PARTITION_ID = 1;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  protected final ExporterConfiguration config;
  protected final DeleterConfiguration deleterConfig = new DeleterConfiguration();
  protected final RetentionConfiguration retentionConfig = new RetentionConfiguration();
  protected ProcessInstanceRetentionMode retentionMode = ProcessInstanceRetentionMode.PI;
  protected final CamundaExporterMetrics metrics =
      new CamundaExporterMetrics(new SimpleMeterRegistry());
  protected final SearchEngineClient engineClient;
  protected final SearchClientAdapter searchClientAdapter;
  protected final OperationTemplate operationTemplate;
  protected final ListViewTemplate listViewTemplate;

  protected DeletionRepositoryIT(final String databaseUrl, final boolean isElastic) {
    final var indexPrefix =
        "deletion" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    config = new ExporterConfiguration();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(databaseUrl);
    config.getConnect().setType(isElastic ? "elasticsearch" : "opensearch");
    config.getConnect().setAwsEnabled(searchDB.isAws());

    final var objectMapper = searchDB.objectMapper();
    if (isElastic) {
      engineClient = new ElasticsearchEngineClient(searchDB.esClient(), objectMapper);
      searchClientAdapter = new SearchClientAdapter(searchDB.esClient(), objectMapper);
    } else {
      engineClient = new OpensearchEngineClient(searchDB.osClient(), objectMapper);
      searchClientAdapter = new SearchClientAdapter(searchDB.osClient(), objectMapper);
    }

    operationTemplate = new OperationTemplate(indexPrefix, isElastic);
    listViewTemplate = new ListViewTemplate(indexPrefix, isElastic);
    retentionConfig.setMinimumAge("1h");
  }

  protected abstract DeletionRepository createRepository();

  @BeforeEach
  void beforeEach() {
    final var indexConfig = new IndexConfiguration();
    indexConfig.setNumberOfShards(
        3); // force more shards so we can verify routing used for deletes etc
    engineClient.createIndex(operationTemplate, indexConfig);
    // ordinal indices are created on demand from the template, as the exporter does
    engineClient.createIndexTemplate(listViewTemplate, indexConfig, true);
    engineClient.createIndex(listViewTemplate, indexConfig);
  }

  @AfterEach
  void afterEach() throws IOException {
    engineClient.deleteIndex(operationTemplate.getFullQualifiedName());
    searchClientAdapter.deleteIndex(listViewTemplate.getIndexPattern());
    searchClientAdapter.deleteIndexTemplate(listViewTemplate.getTemplateName());
  }

  @Test
  void shouldDeleteOnlyDocumentsMatchingFilter() throws IOException {
    // given
    final var indexName = operationTemplate.getFullQualifiedName();
    final var repository = createRepository();
    indexOperation(indexName, 100L);
    indexOperation(indexName, 100L);
    final var toKeep = indexOperation(indexName, 200L);
    indexOperation(indexName, 300L);
    searchClientAdapter.refresh(indexName);

    final var filter =
        FilterByKeysQuery.of(OperationTemplate.PROCESS_INSTANCE_KEY, List.of("100", "300"));

    // when
    final var result = repository.deleteDocumentsById(indexName, filter, Runnable::run);

    // then
    assertThat(result).succeedsWithin(REQUEST_TIMEOUT);
    searchClientAdapter.refresh(indexName);
    assertThat(searchClientAdapter.searchAll(indexName, OperationEntity.class))
        .extracting(OperationEntity::getId)
        .containsExactly(toKeep.getId());
  }

  @Test
  void shouldReturnExpiredProcessInstancesFromOrdinalIndicesOnly() throws IOException {
    // given
    final var expired = OffsetDateTime.now().minusHours(2);
    indexProcessInstance(1, 1L, PARTITION_ID, expired);
    indexProcessInstance(1, 2L, PARTITION_ID, expired);
    // not past the retention period
    indexProcessInstance(1, 3L, PARTITION_ID, OffsetDateTime.now());
    // still running
    indexProcessInstance(1, 4L, PARTITION_ID, null);
    // main index is not targeted
    final var inMainIndex = processInstance(5L, PARTITION_ID, expired);
    searchClientAdapter.index(
        inMainIndex.getId(), listViewTemplate.getFullQualifiedName(), inMainIndex);
    // other partition
    indexProcessInstance(1, 6L, PARTITION_ID + 1, expired);
    searchClientAdapter.refresh(listViewTemplate.getFullQualifiedName());

    // when
    final var result = createRepository().getProcessInstancesNextBatch();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .satisfies(
            batch -> {
              assertThat(batch.storageOrdinal()).isEqualTo(1);
              assertThat(batch.processInstanceKeys()).containsExactlyInAnyOrder(1L, 2L);
              assertThat(batch.rootProcessInstanceKeys()).isEmpty();
            });
  }

  @Test
  void shouldLimitBatchToEntityBatchSizeReturningOldestFirst() throws IOException {
    // given
    deleterConfig.setEntityBatchSize(2);
    final var now = OffsetDateTime.now();
    indexProcessInstance(1, 1L, PARTITION_ID, now.minusHours(3));
    indexProcessInstance(1, 2L, PARTITION_ID, now.minusHours(5));
    indexProcessInstance(1, 3L, PARTITION_ID, now.minusHours(2));
    indexProcessInstance(1, 4L, PARTITION_ID, now.minusHours(4));
    searchClientAdapter.refresh(listViewTemplate.getFullQualifiedName());

    // when
    final var result = createRepository().getProcessInstancesNextBatch();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .extracting(ProcessInstanceDeletionBatch::processInstanceKeys)
        .isEqualTo(List.of(2L, 4L));
  }

  @Test
  void shouldOnlyReturnProcessInstancesFromLowestStorageOrdinal() throws IOException {
    // given
    final var now = OffsetDateTime.now();
    // older, but in a higher storage ordinal
    indexProcessInstance(2, 1L, PARTITION_ID, now.minusHours(5));
    indexProcessInstance(1, 2L, PARTITION_ID, now.minusHours(2));
    indexProcessInstance(1, 3L, PARTITION_ID, now.minusHours(3));
    searchClientAdapter.refresh(listViewTemplate.getFullQualifiedName());

    // when
    final var result = createRepository().getProcessInstancesNextBatch();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .satisfies(
            batch -> {
              assertThat(batch.storageOrdinal()).isEqualTo(1);
              assertThat(batch.processInstanceKeys()).containsExactly(3L, 2L);
            });
  }

  @Test
  void shouldTreatProcessInstancesWithoutStorageOrdinalAsMainIndex() throws IOException {
    // given
    final var now = OffsetDateTime.now();
    final var withoutOrdinal = processInstance(1L, PARTITION_ID, now.minusHours(2));
    searchClientAdapter.index(withoutOrdinal.getId(), ordinalIndex(1), withoutOrdinal);
    // older, but has a storage ordinal so sorts after those without one
    indexProcessInstance(1, 2L, PARTITION_ID, now.minusHours(5));
    searchClientAdapter.refresh(listViewTemplate.getFullQualifiedName());

    // when
    final var result = createRepository().getProcessInstancesNextBatch();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .satisfies(
            batch -> {
              assertThat(batch.storageOrdinal()).isEqualTo(StorageOrdinalRelated.MAIN_INDEX);
              assertThat(batch.processInstanceKeys()).containsExactly(1L);
            });
  }

  @Test
  void shouldReturnEmptyBatchWhenNothingToDelete() {
    // when
    final var result = createRepository().getProcessInstancesNextBatch();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .satisfies(
            batch -> {
              assertThat(batch.isEmpty()).isTrue();
              assertThat(batch.storageOrdinal())
                  .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
            });
  }

  @Test
  void shouldSplitRootAndLegacyProcessInstancesInHierarchyMode() throws IOException {
    // given
    retentionMode = ProcessInstanceRetentionMode.PI_HIERARCHY;
    final var expired = OffsetDateTime.now().minusHours(2);
    final var root = processInstance(1L, PARTITION_ID, expired).setRootProcessInstanceKey(1L);
    final var child =
        processInstance(2L, PARTITION_ID, expired)
            .setRootProcessInstanceKey(1L)
            .setParentProcessInstanceKey(1L);
    final var legacy = processInstance(3L, PARTITION_ID, expired);
    for (final var pi : List.of(root, child, legacy)) {
      pi.setStorageOrdinal(1);
      searchClientAdapter.index(pi.getId(), ordinalIndex(1), pi);
    }
    searchClientAdapter.refresh(listViewTemplate.getFullQualifiedName());

    // when
    final var result = createRepository().getProcessInstancesNextBatch();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .satisfies(
            batch -> {
              assertThat(batch.rootProcessInstanceKeys()).containsExactly(1L);
              assertThat(batch.processInstanceKeys()).containsExactly(3L);
            });
  }

  private String ordinalIndex(final int ordinal) {
    return listViewTemplate.getFullQualifiedName()
        + IndexTemplateDescriptor.ORDINAL_SUFFIX_START
        + "%05d".formatted(ordinal);
  }

  private void indexProcessInstance(
      final int storageOrdinal, final long key, final int partitionId, final OffsetDateTime endDate)
      throws IOException {
    final var pi = processInstance(key, partitionId, endDate).setStorageOrdinal(storageOrdinal);
    searchClientAdapter.index(pi.getId(), ordinalIndex(storageOrdinal), pi);
  }

  private ProcessInstanceForListViewEntity processInstance(
      final long key, final int partitionId, final OffsetDateTime endDate) {
    final var pi =
        new ProcessInstanceForListViewEntity()
            .setProcessInstanceKey(key)
            .setPartitionId(partitionId)
            .setEndDate(endDate);
    pi.setId(String.valueOf(key));
    return pi;
  }

  private OperationEntity indexOperation(final String indexName, final long processInstanceKey)
      throws IOException {
    final var entity =
        new OperationEntity()
            .setId(UUID.randomUUID().toString())
            .setProcessInstanceKey(processInstanceKey);
    searchClientAdapter.index(entity.getId(), indexName, entity);
    return entity;
  }
}
