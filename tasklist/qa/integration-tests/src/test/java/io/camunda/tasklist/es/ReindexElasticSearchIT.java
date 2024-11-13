/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.es.RetryElasticsearchClient.NO_REFRESH;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NO_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.REFRESH_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.v86.migration.es.ReindexPlanElasticSearch;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ReindexElasticSearchIT extends TasklistIntegrationTest {

  private static final String SOURCE_INDEX_123 = "index-1.2.3";

  private static final String SOURCE_INDEX_124 = "index-1.2.4";

  private static final String INDEX_NAME_123 = SOURCE_INDEX_123 + "_";

  private static final String INDEX_NAME_124 = SOURCE_INDEX_124 + "_";

  private static final String INDEX_NAME_ARCHIVER_123 = INDEX_NAME_123 + "2021-05-23";
  private static final String INDEX_NAME_ARCHIVER_124 = INDEX_NAME_124 + "2021-05-23";
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  private String indexPrefix;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @BeforeEach
  public void setUp() {
    indexPrefix = UUID.randomUUID().toString();
  }

  @AfterEach
  public void tearDown() {
    retryElasticsearchClient.deleteIndicesFor(idxName("index-*"));
  }

  private String idxName(String name) {
    return indexPrefix + "-" + name;
  }

  @Test // ZTL-1009
  public void reindexArchivedIndices() throws Exception {
    /// Old version -> before migration
    // create index
    createIndex(idxName(INDEX_NAME_123), List.of(Map.of("test_name", "test_value")));
    // Create archived index
    createIndex(
        idxName(INDEX_NAME_ARCHIVER_123), List.of(Map.of("test_name", "test_value_archived")));
    /// New version -> migration
    // Create new index
    createIndex(idxName(INDEX_NAME_124), List.of());

    retryElasticsearchClient.refresh(idxName("index-*"));
    final ReindexPlanElasticSearch plan =
        ReindexPlanElasticSearch.create()
            .setSrcIndex(idxName(SOURCE_INDEX_123))
            .setDstIndex(idxName(SOURCE_INDEX_124));

    plan.executeOn(retryElasticsearchClient);

    retryElasticsearchClient.refresh(idxName("-index-*"));
    assertThat(retryElasticsearchClient.getIndexNames(idxName("index-*")))
        .containsExactlyInAnyOrder(
            // reindexed indices:
            idxName(INDEX_NAME_124), idxName(INDEX_NAME_ARCHIVER_124),
            // old indices:
            idxName(INDEX_NAME_123), idxName(INDEX_NAME_ARCHIVER_123));
  }

  @Test // ZTL-1008
  public void resetIndexSettings() {
    /// Old version -> before migration
    // create index
    createIndex(idxName(INDEX_NAME_123), List.of(Map.of("test_name", "test_value")));
    // set reindex settings
    final Settings settings =
        Settings.builder()
            .put(NUMBERS_OF_REPLICA, NO_REPLICA)
            .put(REFRESH_INTERVAL, NO_REFRESH)
            .build();
    retryElasticsearchClient.setIndexSettingsFor(settings, idxName(INDEX_NAME_123));
    final Map<String, String> reindexSettings =
        retryElasticsearchClient.getIndexSettingsFor(
            idxName(INDEX_NAME_123), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings)
        .containsEntry(NUMBERS_OF_REPLICA, NO_REPLICA)
        .containsEntry(REFRESH_INTERVAL, NO_REFRESH);
    // Migrator uses this
    assertThat(retryElasticsearchClient.getOrDefaultNumbersOfReplica(idxName(INDEX_NAME_123), "5"))
        .isEqualTo("5");
    assertThat(retryElasticsearchClient.getOrDefaultRefreshInterval(idxName(INDEX_NAME_123), "2"))
        .isEqualTo("2");
  }

  private void createIndex(final String indexName, List<Map<String, String>> documents) {
    final Map<String, ?> mapping =
        Map.of("properties", Map.of("test_name", Map.of("type", "keyword")));
    retryElasticsearchClient.createIndex(new CreateIndexRequest(indexName).mapping(mapping));
    assertThat(retryElasticsearchClient.getIndexNames(idxName("index*"))).contains(indexName);
    documents.forEach(
        (Map<String, String> doc) ->
            retryElasticsearchClient.createOrUpdateDocument(
                indexName, UUID.randomUUID().toString(), doc));
  }
}
