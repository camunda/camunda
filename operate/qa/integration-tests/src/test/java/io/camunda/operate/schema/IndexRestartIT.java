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
package io.camunda.operate.schema;

import static io.camunda.operate.schema.SchemaManager.NUMBERS_OF_REPLICA;
import static io.camunda.operate.schema.SchemaManager.REFRESH_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.backup.Prio4Backup;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IndexRestartIT {
  @Autowired private OperateProperties operateProperties;
  @Autowired private TestSearchRepository searchRepository;
  @Autowired private SchemaManager schemaManager;
  private List<AbstractIndexDescriptor> indexDescriptors;
  private String testDescriptorName;

  @BeforeAll
  public void init() throws Exception {
    schemaManager.createSchema();
    final TestDescriptor testDescriptor = new TestDescriptor();
    indexDescriptors = List.of(testDescriptor);
    testDescriptorName = testDescriptor.getFullQualifiedName();
    ReflectionTestUtils.setField(schemaManager, "indexDescriptors", indexDescriptors);
    createIndex(idxName(""), List.of(Map.of("test_name1", "test_value1")));
    createIndex(idxName("index1"), List.of(Map.of("test_name1", "test_value1")));
    createIndex(idxName("index2"), List.of(Map.of("test_name2", "test_value2")));
    createIndex(idxName("index3"), List.of(Map.of("test_name3", "test_value3")));
  }

  @AfterAll
  public void tearDown() {
    schemaManager.deleteIndicesFor(idxName("*"));
  }

  private String idxName(final String name) {
    return testDescriptorName + name;
  }

  @Test
  public void testCheckAndUpdateIndices() {

    int defaultNumberofReplicas = 3;
    final String defaultRefreshInterval = "4s";

    final Map<String, String> currentSettings =
        schemaManager.getIndexSettingsFor(idxName(""), NUMBERS_OF_REPLICA);
    if (Integer.parseInt(currentSettings.get(NUMBERS_OF_REPLICA)) == defaultNumberofReplicas) {
      defaultNumberofReplicas++;
    }
    if (DatabaseInfo.isOpensearch()) {
      operateProperties.getOpensearch().setNumberOfReplicas(defaultNumberofReplicas);
      operateProperties.getOpensearch().setRefreshInterval(defaultRefreshInterval);
    } else {
      operateProperties.getElasticsearch().setNumberOfReplicas(defaultNumberofReplicas);
      operateProperties.getElasticsearch().setRefreshInterval(defaultRefreshInterval);
    }

    // update number of replicas for each index
    schemaManager.checkAndUpdateIndices();

    final Map<String, String> reindexSettings1 =
        schemaManager.getIndexSettingsFor(idxName("index1"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings1)
        .containsEntry(NUMBERS_OF_REPLICA, String.valueOf(defaultNumberofReplicas))
        .containsEntry(
            REFRESH_INTERVAL, DatabaseInfo.isElasticsearch() ? null : defaultRefreshInterval);

    final Map<String, String> reindexSettings2 =
        schemaManager.getIndexSettingsFor(idxName("index2"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings2)
        .containsEntry(NUMBERS_OF_REPLICA, String.valueOf(defaultNumberofReplicas))
        .containsEntry(
            REFRESH_INTERVAL, DatabaseInfo.isElasticsearch() ? null : defaultRefreshInterval);

    final Map<String, String> reindexSettings3 =
        schemaManager.getIndexSettingsFor(idxName("index3"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings3)
        .containsEntry(NUMBERS_OF_REPLICA, String.valueOf(defaultNumberofReplicas))
        .containsEntry(
            REFRESH_INTERVAL, DatabaseInfo.isElasticsearch() ? null : defaultRefreshInterval);

    assertThat(schemaManager.getOrDefaultNumbersOfReplica(idxName("index1"), "2"))
        .isEqualTo(Integer.toString(defaultNumberofReplicas));

    assertThat(schemaManager.getOrDefaultRefreshInterval(idxName("index1"), "1s"))
        .isEqualTo(DatabaseInfo.isElasticsearch() ? "1s" : defaultRefreshInterval);

    assertThat(schemaManager.getOrDefaultNumbersOfReplica(idxName("index2"), "2"))
        .isEqualTo(Integer.toString(defaultNumberofReplicas));

    assertThat(schemaManager.getOrDefaultRefreshInterval(idxName("index2"), "1s"))
        .isEqualTo(DatabaseInfo.isElasticsearch() ? "1s" : defaultRefreshInterval);

    assertThat(schemaManager.getOrDefaultNumbersOfReplica(idxName("index3"), "2"))
        .isEqualTo(Integer.toString(defaultNumberofReplicas));

    assertThat(schemaManager.getOrDefaultRefreshInterval(idxName("index3"), "1s"))
        .isEqualTo(DatabaseInfo.isElasticsearch() ? "1s" : defaultRefreshInterval);
  }

  private void createIndex(final String indexName, final List<Map<String, String>> documents)
      throws Exception {
    final Map<String, ?> mapping =
        Map.of("properties", Map.of("test_name", Map.of("type", "keyword")));
    searchRepository.createIndex(indexName, mapping);
    for (final var document : documents) {
      searchRepository.createOrUpdateDocument(indexName, UUID.randomUUID().toString(), document);
    }
  }

  public class TestDescriptor extends AbstractIndexDescriptor implements Prio4Backup {

    public static final String INDEX_NAME = "test_descriptor";

    @Override
    public String getIndexName() {
      return INDEX_NAME;
    }

    @Override
    public String getVersion() {
      return "8.3.0";
    }

    @Override
    public String getFullQualifiedName() {
      return String.format("%s-%s-%s_", "operate", getIndexName(), getVersion());
    }
  }
}
