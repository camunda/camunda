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
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.schema.migration.os.ReindexPlanOpenSearch;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestUtil;
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
