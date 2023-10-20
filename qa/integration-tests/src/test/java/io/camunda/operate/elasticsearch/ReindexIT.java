/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.migration.Plan;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.OperateAbstractIT;
import org.apache.logging.log4j.junit.LoggerContextRule;
import org.apache.logging.log4j.test.appender.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.IntStream;

import static io.camunda.operate.schema.SchemaManager.NO_REFRESH;
import static io.camunda.operate.schema.SchemaManager.NO_REPLICA;
import static io.camunda.operate.schema.SchemaManager.NUMBERS_OF_REPLICA;
import static io.camunda.operate.schema.SchemaManager.REFRESH_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

public class ReindexIT extends OperateAbstractIT {

  @Autowired
  private TestSearchRepository searchRepository;
  @Autowired
  private SchemaManager schemaManager;
  @Autowired
  private MigrationProperties migrationProperties;

  @Autowired
  private BeanFactory beanFactory;

  private String indexPrefix;

  @ClassRule
  public static final LoggerContextRule loggerRule = new LoggerContextRule("log4j2-listAppender.xml");

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

  @Test
  public void logReindexProgress() throws Exception {
    // given
    ListAppender logListAppender = loggerRule.getListAppender("OperateElasticLogsList");
    // slow the reindex down, to increase chance of sub 100% progress logged
    migrationProperties.setReindexBatchSize(1);
    /// Old index
    createIndex(idxName("index-1.2.3_"), IntStream.range(0, 15000).mapToObj(i -> Map.of("test_name", "test_value" + i)).toList());
    /// New index
    createIndex(idxName("index-1.2.4_"), List.of());

    schemaManager.refresh(idxName("index-*"));
    Plan plan = beanFactory.getBean(ReindexPlan.class)
      .setSrcIndex(idxName("index-1.2.3"))
      .setDstIndex(idxName("index-1.2.4"));

    // when
    plan.executeOn(schemaManager);
    schemaManager.refresh(idxName("index-*"));

    // then
    assertThat(schemaManager.getIndexNames(idxName("index-*")))
      .containsExactlyInAnyOrder(
        // reindexed indices:
        idxName("index-1.2.4_"),
        // old indices:
        idxName("index-1.2.3_"));

    var events = logListAppender.getEvents();
    final List<String> progressLogMessages = events.stream()
      .filter(event -> event.getMessage().getFormat().startsWith("TaskId: "))
      .map(event -> event.getMessage().getFormattedMessage())
      .toList();
    assertThat(progressLogMessages)
      // we expect at least a `100%` entry, on varying performance we fuzzily also assert sub 100% values
      .hasSizeGreaterThanOrEqualTo(1)
        // We use '.' regex expression for number format with "." or ","
      .allSatisfy(logMessage -> assertThat(logMessage).matches("TaskId: .+:.+, Progress: \\d{1,3}.\\d{2}%"))
      .last()
      .satisfies(logMessage -> assertThat(logMessage).matches("TaskId: .+:.+, Progress: 100.00%"));
  }

  @Test // OPE-1311
  public void resetIndexSettings() throws Exception {
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
        .containsEntry(NUMBERS_OF_REPLICA, DatabaseInfo.isOpensearch()?null:NO_REPLICA)
        .containsEntry(REFRESH_INTERVAL, DatabaseInfo.isOpensearch()?null:NO_REFRESH);
    // Migrator uses this
    assertThat(schemaManager
        .getOrDefaultNumbersOfReplica(idxName("index-1.2.3_"), "5")).isEqualTo("5");
    assertThat(schemaManager
        .getOrDefaultRefreshInterval(idxName("index-1.2.3_"), "2")).isEqualTo("2");
  }

  private void createIndex(final String indexName, List<Map<String, String>> documents) throws Exception {
    if(DatabaseInfo.isElasticsearch()) {
      final Map<String, ?> mapping = Map.of("properties",
          Map.of("test_name",
              Map.of("type", "keyword")));
      searchRepository.createIndex(indexName, mapping);
      assertThat(schemaManager.getIndexNames(idxName("index*")))
          .contains(indexName);
    }
    if(documents.isEmpty() && DatabaseInfo.isOpensearch()){
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), Map.of());
    }
    for(var document: documents) {
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), document);
    }
  }
}
