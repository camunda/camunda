/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.schema.SchemaManager.NO_REFRESH;
import static io.camunda.operate.schema.SchemaManager.NO_REPLICA;
import static io.camunda.operate.schema.SchemaManager.NUMBERS_OF_REPLICA;
import static io.camunda.operate.schema.SchemaManager.REFRESH_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.Plan;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.logging.log4j.junit.LoggerContextRule;
import org.apache.logging.log4j.test.appender.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ReindexIT extends OperateAbstractIT {

  @ClassRule
  public static final LoggerContextRule LOGGER_RULE =
      new LoggerContextRule("log4j2-listAppender.xml");

  @Autowired private TestSearchRepository searchRepository;
  @Autowired private SchemaManager schemaManager;
  @Autowired private MigrationProperties migrationProperties;
  @Autowired private BeanFactory beanFactory;
  private String indexPrefix;

  @Before
  public void setUp() {
    indexPrefix = UUID.randomUUID().toString();
  }

  @After
  public void tearDown() {
    schemaManager.deleteIndicesFor(idxName("index-*"));
  }

  private String idxName(final String name) {
    return indexPrefix + "-" + name;
  }

  @Test // OPE-1312
  public void reindexArchivedIndices() throws Exception {
    /// Old version -> before migration
    // create index
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name", "test_value")));
    // Create archived index
    createIndex(
        idxName("index-1.2.3_2021-05-23"), List.of(Map.of("test_name", "test_value_archived")));
    /// New version -> migration
    // Create new index
    createIndex(idxName("index-1.2.4_"), List.of());

    schemaManager.refresh(idxName("index-*"));
    final Plan plan =
        beanFactory
            .getBean(ReindexPlan.class)
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
    final ListAppender logListAppender = LOGGER_RULE.getListAppender("OperateElasticLogsList");
    // slow the reindex down, to increase chance of sub 100% progress logged
    migrationProperties.setReindexBatchSize(1);
    /// Old index
    createIndex(
        idxName("index-1.2.3_"),
        IntStream.range(0, 15000).mapToObj(i -> Map.of("test_name", "test_value" + i)).toList());
    /// New index
    createIndex(idxName("index-1.2.4_"), List.of());

    schemaManager.refresh(idxName("index-*"));
    final Plan plan =
        beanFactory
            .getBean(ReindexPlan.class)
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

    final var events = logListAppender.getEvents();
    final List<String> progressLogMessages =
        events.stream()
            .filter(event -> event.getMessage().getFormat().startsWith("TaskId: "))
            .map(event -> event.getMessage().getFormattedMessage())
            .toList();
    assertThat(progressLogMessages)
        // we expect at least a `100%` entry, on varying performance we fuzzily also assert sub 100%
        // values
        .hasSizeGreaterThanOrEqualTo(1)
        // We use '.' regex expression for number format with "." or ","
        .allSatisfy(
            logMessage ->
                assertThat(logMessage).matches("TaskId: .+:.+, Progress: \\d{1,3}.\\d{2}%"))
        .last()
        .satisfies(
            logMessage -> assertThat(logMessage).matches("TaskId: .+:.+, Progress: 100.00%"));
  }

  @Test // OPE-1311
  public void resetIndexSettings() throws Exception {
    /// Old version -> before migration
    // create index
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name", "test_value")));
    // set reindex settings
    schemaManager.setIndexSettingsFor(
        Map.of(
            NUMBERS_OF_REPLICA, NO_REPLICA,
            REFRESH_INTERVAL, NO_REFRESH),
        idxName("index-1.2.3_"));
    final Map<String, String> reindexSettings =
        schemaManager.getIndexSettingsFor(
            idxName("index-1.2.3_"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings)
        .containsEntry(NUMBERS_OF_REPLICA, NO_REPLICA)
        .containsEntry(REFRESH_INTERVAL, NO_REFRESH);
    // Migrator uses this
    assertThat(schemaManager.getOrDefaultNumbersOfReplica(idxName("index-1.2.3_"), "5"))
        .isEqualTo("5");
    assertThat(schemaManager.getOrDefaultRefreshInterval(idxName("index-1.2.3_"), "2"))
        .isEqualTo("2");
  }

  private void createIndex(final String indexName, final List<Map<String, String>> documents)
      throws Exception {
    if (DatabaseInfo.isElasticsearch()) {
      final Map<String, ?> mapping =
          Map.of("properties", Map.of("test_name", Map.of("type", "keyword")));
      searchRepository.createIndex(indexName, mapping);
      assertThat(schemaManager.getIndexNames(idxName("index*"))).contains(indexName);
    }
    if (documents.isEmpty() && DatabaseInfo.isOpensearch()) {
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), Map.of());
    }
    for (final var document : documents) {
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), document);
    }
  }
}
