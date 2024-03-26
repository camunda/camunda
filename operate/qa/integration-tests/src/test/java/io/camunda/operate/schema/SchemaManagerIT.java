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

import static io.camunda.operate.schema.SchemaManager.NO_REFRESH;
import static io.camunda.operate.schema.SchemaManager.NO_REPLICA;
import static io.camunda.operate.schema.SchemaManager.NUMBERS_OF_REPLICA;
import static io.camunda.operate.schema.SchemaManager.REFRESH_INTERVAL;
import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.web.WebAppConfiguration;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@WebAppConfiguration
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
@AutoConfigureMockMvc
public class SchemaManagerIT {
  public static final String DEFAULT_USER = "testuser";
  @MockBean protected UserService userService;
  @MockBean protected TenantService tenantService;
  @Autowired OperateProperties operateProperties;
  @Autowired private TestSearchRepository searchRepository;
  @Autowired private SchemaManager schemaManager;
  private String indexPrefix;

  @BeforeEach
  public void before() {
    schemaManager.deleteIndicesFor(idxName("index-*"));
    when(userService.getCurrentUser())
        .thenReturn(
            new UserDto().setUserId(DEFAULT_USER).setPermissions(List.of(Permission.WRITE)));
    mockTenantResponse();
  }

  @AfterEach
  public void tearDown() {
    schemaManager.deleteIndicesFor(idxName("index-*"));
  }

  private String idxName(final String name) {
    return indexPrefix + "-" + name;
  }

  @Test // OPE-1311
  public void testCheckAndUpdateIndices() throws Exception {

    final int expectedNumberOfReplicas = 5;
    if (DatabaseInfo.isOpensearch()) {
      operateProperties.getOpensearch().setNumberOfReplicas(expectedNumberOfReplicas);
    } else {
      operateProperties.getElasticsearch().setNumberOfReplicas(expectedNumberOfReplicas);
    }
    /// Old version -> before migration
    // create index
    createIndex(idxName("index-1.2.3_"), List.of(Map.of("test_name1", "test_value1")));
    createIndex(idxName("index-2.3.4_"), List.of(Map.of("test_name2", "test_value2")));
    createIndex(idxName("index-3.4.5_"), List.of(Map.of("test_name3", "test_value3")));

    // set reindex settings
    schemaManager.setIndexSettingsFor(
        Map.of(
            NUMBERS_OF_REPLICA, NO_REPLICA,
            REFRESH_INTERVAL, NO_REFRESH),
        idxName("index-1.2.3_"));
    schemaManager.setIndexSettingsFor(
        Map.of(REFRESH_INTERVAL, NO_REFRESH), idxName("index-2.3.4_"));
    schemaManager.setIndexSettingsFor(
        Map.of(NUMBERS_OF_REPLICA, "5", REFRESH_INTERVAL, NO_REFRESH), idxName("index-3.4.5_"));

    // update number of replicas for each index
    schemaManager.checkAndUpdateIndices();

    final Map<String, String> reindexSettings1 =
        schemaManager.getIndexSettingsFor(
            idxName("index-1.2.3_"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings1)
        .containsEntry(
            NUMBERS_OF_REPLICA,
            DatabaseInfo.isOpensearch() ? null : String.valueOf(expectedNumberOfReplicas))
        .containsEntry(REFRESH_INTERVAL, DatabaseInfo.isOpensearch() ? null : NO_REFRESH);

    final Map<String, String> reindexSettings2 =
        schemaManager.getIndexSettingsFor(
            idxName("index-2.3.4_"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings2)
        .containsEntry(
            NUMBERS_OF_REPLICA,
            DatabaseInfo.isOpensearch() ? null : String.valueOf(expectedNumberOfReplicas))
        .containsEntry(REFRESH_INTERVAL, DatabaseInfo.isOpensearch() ? null : NO_REFRESH);

    final Map<String, String> reindexSettings3 =
        schemaManager.getIndexSettingsFor(
            idxName("index-3.4.5_"), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    assertThat(reindexSettings3)
        .containsEntry(
            NUMBERS_OF_REPLICA,
            DatabaseInfo.isOpensearch() ? null : String.valueOf(expectedNumberOfReplicas))
        .containsEntry(REFRESH_INTERVAL, DatabaseInfo.isOpensearch() ? null : NO_REFRESH);
    // Migrator uses this
    assertThat(schemaManager.getOrDefaultNumbersOfReplica(idxName("index-1.2.3_"), "5"))
        .isEqualTo("5");
    assertThat(schemaManager.getOrDefaultRefreshInterval(idxName("index-1.2.3_"), "2"))
        .isEqualTo("2");
  }

  private void mockTenantResponse() {
    doReturn(TenantService.AuthenticatedTenants.allTenants())
        .when(tenantService)
        .getAuthenticatedTenants();
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
