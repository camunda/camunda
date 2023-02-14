/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import static io.camunda.operate.es.RetryElasticsearchClient.NO_REFRESH;
import static io.camunda.operate.es.RetryElasticsearchClient.NO_REPLICA;
import static io.camunda.operate.es.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.operate.es.RetryElasticsearchClient.REFRESH_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.schema.migration.Plan;
import io.camunda.operate.util.OperateIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ReindexIT extends OperateIntegrationTest {

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  private String indexPrefix;

  @Before
  public void setUp() {
    indexPrefix = UUID.randomUUID().toString();
  }

  @After
  public void tearDown() {
    retryElasticsearchClient.deleteIndicesFor(idxName("index-*"));
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

    retryElasticsearchClient.refresh(idxName("index-*"));
    Plan plan = Plan.forReindex()
        .setSrcIndex(idxName("index-1.2.3"))
        .setDstIndex(idxName("index-1.2.4"));

    plan.executeOn(retryElasticsearchClient);

    retryElasticsearchClient.refresh(idxName("index-*"));
    assertThat(retryElasticsearchClient.getIndexNames(idxName("index-*")))
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
    final Settings settings = Settings.builder()
        .put(NUMBERS_OF_REPLICA, NO_REPLICA)
        .put(REFRESH_INTERVAL, NO_REFRESH).build();
    retryElasticsearchClient.setIndexSettingsFor(settings, idxName("index-1.2.3_"));
    Map<String,String> reindexSettings = retryElasticsearchClient
        .getIndexSettingsFor(idxName("index-1.2.3_"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings)
        .containsEntry(NUMBERS_OF_REPLICA, NO_REPLICA)
        .containsEntry(REFRESH_INTERVAL, NO_REFRESH);
    // Migrator uses this
    assertThat(retryElasticsearchClient
        .getOrDefaultNumbersOfReplica(idxName("index-1.2.3_"), "5")).isEqualTo("5");
    assertThat(retryElasticsearchClient
        .getOrDefaultRefreshInterval(idxName("index-1.2.3_"), "2")).isEqualTo("2");
  }

  private void createIndex(final String indexName, List<Map<String, String>> documents) {
    final Map<String, ?> mapping = Map.of("properties",
        Map.of("test_name",
            Map.of("type", "keyword")));
    retryElasticsearchClient.createIndex(new CreateIndexRequest(indexName).mapping(mapping));
    assertThat(retryElasticsearchClient.getIndexNames(idxName("index*")))
        .contains(indexName);
    documents.forEach((Map<String, String> doc) ->
        retryElasticsearchClient.createOrUpdateDocument(indexName,UUID.randomUUID().toString(), doc)
    );
  }
}
