/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.MigrationPlanFactory;
import io.camunda.operate.schema.migration.Plan;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(MockitoExtension.class)
public class ReindexIT extends OperateSearchAbstractIT {

  private String indexPrefix;
  @Autowired private SchemaManager schemaManager;
  @Autowired private TestSearchRepository searchRepository;
  @Autowired private MigrationPlanFactory migrationPlanFactory;

  @Override
  protected void runAdditionalBeforeEachSetup() throws Exception {
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name", "test_value")));
    schemaManager.refresh(idxName("index-*"));
  }

  @AfterAll
  @Override
  public void afterAllTeardown() {
    schemaManager.deleteIndicesFor(idxName("index-*"));
    super.afterAllTeardown();
  }

  @Test // OPE-1312
  public void testReindexArchivedIndices() throws Exception {
    // Create archived index
    createIndex(
        idxName("index-1.2.3_2021-05-23"), List.of(Map.of("test_name", "test_value_archived")));
    // Create new index
    createIndex(idxName("index-1.2.4_"), List.of());

    schemaManager.refresh(idxName("index-*"));

    final Plan plan =
        migrationPlanFactory
            .createReindexPlan()
            .setSrcIndex(idxName("index-1.2.3"))
            .setDstIndex(idxName("index-1.2.4"));

    plan.executeOn(schemaManager);

    schemaManager.refresh(idxName("index-*"));
    assertThat(schemaManager.getIndexNames(idxName("index-*")))
        .containsExactlyInAnyOrder(
            // reindexed indices:
            idxName("index-1.2.4_"), idxName("index-1.2.4_2021-05-23"),
            // old indices:
            idxName("index-1.2.3_"), idxName("index-1.2.3_2021-05-23"));
  }

  private void createIndex(final String indexName, final List<Map<String, String>> documents)
      throws Exception {
    if (DatabaseInfo.isElasticsearch()) {
      final Map<String, ?> mapping =
          Map.of("properties", Map.of("test_name", Map.of("type", "keyword")));
      searchRepository.createIndex(indexName, mapping);
    }
    if (documents.isEmpty()) {
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), Map.of());
    }
    for (final var document : documents) {
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), document);
    }
  }

  private String idxName(final String name) {
    return indexPrefix + "-" + name;
  }
}
