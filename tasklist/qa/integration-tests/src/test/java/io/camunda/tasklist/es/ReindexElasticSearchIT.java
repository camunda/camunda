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
package io.camunda.tasklist.es;

import static io.camunda.tasklist.es.RetryElasticsearchClient.NO_REFRESH;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NO_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.REFRESH_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.schema.migration.es.ReindexPlanElasticSearch;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestUtil;
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
