/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.it;

import static io.camunda.migration.process.adapter.Adapter.PROCESSOR_STEP_ID;
import static io.camunda.migration.process.adapter.Adapter.STEP_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest.Builder;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.MigrationTest;
import io.camunda.migration.commons.configuration.ConfigurationType;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.migration.process.ProcessMigrator;
import io.camunda.migration.process.TestData;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public abstract class AdapterTest extends MigrationTest {
  protected static final String MISCONFIGURED_PREFIX = "misconfigured";
  protected IndexDescriptor misconfiguredIndex;

  @Override
  protected Migrator supplyMigrator(
      final ConnectConfiguration connectConfiguration,
      final MigrationConfiguration migrationConfiguration,
      final MeterRegistry meterRegistry) {
    final var migrationProperties = new MigrationProperties();
    migrationProperties.setMigration(Map.of(ConfigurationType.PROCESS, migrationConfiguration));
    return new ProcessMigrator(migrationProperties, connectConfiguration, meterRegistry);
  }

  @Override
  protected IndexDescriptor[] requiredIndices(final String prefix, final boolean isElasticsearch) {
    misconfiguredIndex =
        new TestData.MisconfiguredProcessIndex(MISCONFIGURED_PREFIX, isElasticsearch);
    return new IndexDescriptor[] {
      new ProcessIndex(ES_CONFIGURATION.getIndexPrefix(), isElasticsearch),
      new MigrationRepositoryIndex(ES_CONFIGURATION.getIndexPrefix(), isElasticsearch),
      new ImportPositionIndex(ES_CONFIGURATION.getIndexPrefix(), isElasticsearch),
      misconfiguredIndex
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void writeToMisconfiguredProcessToIndex(final ProcessEntity entity) throws IOException {
    final var document =
        Map.of(
            "id",
            entity.getId(),
            "key",
            entity.getKey(),
            "bpmnXml",
            entity.getBpmnXml(),
            "version",
            entity.getVersion(),
            "bpmnProcessId",
            entity.getBpmnProcessId());
    if (isElasticsearch) {
      esClient.index(
          new co.elastic.clients.elasticsearch.core.IndexRequest.Builder()
              .index(misconfiguredIndex.getFullQualifiedName())
              .document(document)
              .id(entity.getId())
              .build());
    } else {
      osClient.index(
          new org.opensearch.client.opensearch.core.IndexRequest.Builder<>()
              .index(misconfiguredIndex.getFullQualifiedName())
              .document(document)
              .id(entity.getId())
              .build());
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void writeProcessToIndex(final ProcessEntity entity) throws IOException {
    if (isElasticsearch) {
      esClient.index(
          new Builder()
              .index(indexFqnForClass(ProcessIndex.class))
              .document(entity)
              .id(entity.getId())
              .build());
    } else {
      osClient.index(
          new org.opensearch.client.opensearch.core.IndexRequest.Builder<>()
              .index(indexFqnForClass(ProcessIndex.class))
              .document(entity)
              .id(entity.getId())
              .build());
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void writeProcessorStepToIndex(final String processDefinitionId) throws IOException {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(processDefinitionId);
    step.setApplied(true);
    step.setIndexName(ProcessIndex.INDEX_NAME);
    step.setDescription(STEP_DESCRIPTION);
    step.setVersion(VersionUtil.getVersion());
    if (isElasticsearch) {
      esClient.index(
          new IndexRequest.Builder()
              .index(indexFqnForClass(MigrationRepositoryIndex.class))
              .document(step)
              .id(PROCESSOR_STEP_ID)
              .refresh(Refresh.True)
              .build());
    } else {
      osClient.index(
          new org.opensearch.client.opensearch.core.IndexRequest.Builder<>()
              .index(indexFqnForClass(MigrationRepositoryIndex.class))
              .document(step)
              .id(PROCESSOR_STEP_ID)
              .refresh(org.opensearch.client.opensearch._types.Refresh.True)
              .build());
    }
  }

  protected void writeImportPositionToIndex(final ImportPositionEntity... importPositionEntities)
      throws IOException {
    if (isElasticsearch) {
      final var req = new BulkRequest.Builder().refresh(Refresh.True);
      Arrays.stream(importPositionEntities)
          .forEach(
              imp ->
                  req.operations(
                      op ->
                          op.index(
                              e ->
                                  e.id(imp.getId())
                                      .document(imp)
                                      .index(indexFqnForClass(ImportPositionIndex.class)))));

      esClient.bulk(req.build());
    } else {
      final var req =
          new org.opensearch.client.opensearch.core.BulkRequest.Builder()
              .refresh(org.opensearch.client.opensearch._types.Refresh.True);
      Arrays.stream(importPositionEntities)
          .forEach(
              imp ->
                  req.operations(
                      op ->
                          op.index(
                              e ->
                                  e.id(imp.getId())
                                      .document(imp)
                                      .index(indexFqnForClass(ImportPositionIndex.class)))));

      osClient.bulk(req.build());
    }
  }

  protected void assertProcessorStepContentIsStored(final String processDefinitionId)
      throws IOException {
    final var records =
        readRecords(ProcessorStep.class, indexFqnForClass(MigrationRepositoryIndex.class));
    assertThat(records.size()).isOne();
    assertThat(records.getFirst().getContent()).isEqualTo(String.valueOf(processDefinitionId));
  }
}
