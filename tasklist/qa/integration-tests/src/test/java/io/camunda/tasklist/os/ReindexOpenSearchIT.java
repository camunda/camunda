/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.v86.migration.os.ReindexPlanOpenSearch;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.springframework.beans.factory.annotation.Autowired;

public class ReindexOpenSearchIT extends TasklistIntegrationTest {

  private static final String SOURCE_INDEX_123 = "index-1.2.3";

  private static final String SOURCE_INDEX_124 = "index-1.2.4";

  private static final String INDEX_NAME_123 = SOURCE_INDEX_123 + "_";

  private static final String INDEX_NAME_124 = SOURCE_INDEX_124 + "_";

  private static final String INDEX_NAME_ARCHIVER_123 = INDEX_NAME_123 + "2021-05-23";
  private static final String INDEX_NAME_ARCHIVER_124 = INDEX_NAME_124 + "2021-05-23";

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  private String indexPrefix;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @BeforeEach
  public void setUp() {
    indexPrefix = UUID.randomUUID().toString();
  }

  @AfterEach
  public void tearDown() {
    retryOpenSearchClient.deleteIndicesFor(idxName("index-*"));
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

    retryOpenSearchClient.refresh(idxName("index-*"));
    final ReindexPlanOpenSearch plan =
        ReindexPlanOpenSearch.create()
            .setSrcIndex(idxName(SOURCE_INDEX_123))
            .setDstIndex(idxName(SOURCE_INDEX_124));

    plan.executeOn(retryOpenSearchClient);

    retryOpenSearchClient.refresh(idxName("-index-*"));
    assertThat(retryOpenSearchClient.getIndexNames(idxName("index-*")))
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
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name", "test_value")));
    // set reindex settings
    final IndexSettings settings =
        new IndexSettings.Builder()
            .numberOfReplicas(RetryOpenSearchClient.NO_REPLICA)
            .refreshInterval(t -> t.time(RetryOpenSearchClient.NO_REFRESH))
            .build();
    retryOpenSearchClient.setIndexSettingsFor(settings, idxName(INDEX_NAME_123));
    final IndexSettings reindexSettings =
        retryOpenSearchClient.getIndexSettingsFor(
            idxName("index-1.2.3_"),
            RetryOpenSearchClient.NUMBERS_OF_REPLICA,
            RetryOpenSearchClient.REFRESH_INTERVAL);
    assertThat(reindexSettings.numberOfReplicas()).isEqualTo(RetryOpenSearchClient.NO_REPLICA);
    assertThat(reindexSettings.refreshInterval().time())
        .isEqualTo(RetryOpenSearchClient.NO_REFRESH);
    // Migrator uses this
    assertThat(retryOpenSearchClient.getOrDefaultNumbersOfReplica(idxName(INDEX_NAME_123), "5"))
        .isEqualTo("5");
    assertThat(retryOpenSearchClient.getOrDefaultRefreshInterval(idxName(INDEX_NAME_123), "2"))
        .isEqualTo("2");
  }

  private void createIndex(final String indexName, List<Map<String, String>> documents) {

    retryOpenSearchClient.createIndex(new CreateIndexRequest.Builder().index(indexName).build());

    assertThat(retryOpenSearchClient.getIndexNames(idxName("index*"))).contains(indexName);
    documents.forEach(
        (Map<String, String> doc) ->
            retryOpenSearchClient.createOrUpdateDocument(
                indexName, UUID.randomUUID().toString(), doc));
  }
}
