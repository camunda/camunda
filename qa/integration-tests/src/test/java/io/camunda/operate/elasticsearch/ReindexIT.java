/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.schema.SchemaManager.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.Plan;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.OperateIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ReindexIT extends OperateIntegrationTest {

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired
  private SchemaManager schemaManager;

  @Autowired
  private BeanFactory beanFactory;

  private String indexPrefix;

  @Before
  public void setUp() {
    indexPrefix = UUID.randomUUID().toString();
  }

  @After
  public void tearDown() {
    schemaManager.deleteIndicesFor(idxName("index-*"));
  }

  private String idxName(String name) {
    return indexPrefix + "-" + name;
  }

  @Test // OPE-1312
  public void reindexArchivedIndices() throws Exception {
    /// Old version -> before migration
    // create index
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name", "test_value")));
    // Create archived index
    createIndex(idxName("index-1.2.3_2021-05-23"),
        List.of(Map.of("test_name", "test_value_archived")));
    /// New version -> migration
    // Create new index
    createIndex(idxName("index-1.2.4_"), List.of());

    schemaManager.refresh(idxName("index-*"));
    Plan plan = beanFactory.getBean(ReindexPlan.class)
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

  @Test // OPE-1311
  public void resetIndexSettings() {
    /// Old version -> before migration
    // create index
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name", "test_value")));
    // set reindex settings
    schemaManager.setIndexSettingsFor(Map.of(
        NUMBERS_OF_REPLICA, NO_REPLICA,
        REFRESH_INTERVAL, NO_REFRESH), idxName("index-1.2.3_"));
    Map<String,String> reindexSettings = schemaManager
        .getIndexSettingsFor(idxName("index-1.2.3_"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings)
        .containsEntry(NUMBERS_OF_REPLICA, NO_REPLICA)
        .containsEntry(REFRESH_INTERVAL, NO_REFRESH);
    // Migrator uses this
    assertThat(schemaManager
        .getOrDefaultNumbersOfReplica(idxName("index-1.2.3_"), "5")).isEqualTo("5");
    assertThat(schemaManager
        .getOrDefaultRefreshInterval(idxName("index-1.2.3_"), "2")).isEqualTo("2");
  }

  private void createIndex(final String indexName, List<Map<String, String>> documents) {
    final Map<String, ?> mapping = Map.of("properties",
        Map.of("test_name",
            Map.of("type", "keyword")));
    schemaManager.createIndex(/*new CreateIndexRequest(*/indexName, mapping);
    assertThat(schemaManager.getIndexNames(idxName("index*")))
        .contains(indexName);
    documents.forEach((Map<String, String> doc) ->
        retryElasticsearchClient.createOrUpdateDocument(indexName,UUID.randomUUID().toString(), doc)
    );

  }
}
