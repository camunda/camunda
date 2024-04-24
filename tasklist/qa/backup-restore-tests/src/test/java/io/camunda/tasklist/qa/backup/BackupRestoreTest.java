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
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME;
import static io.camunda.tasklist.util.CollectionUtil.asMap;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.backup.generator.BackupRestoreDataGenerator;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.util.TasklistPropertiesUtil;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.repositories.fs.FsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.CreateRepositoryRequest;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class})
public class BackupRestoreTest {

  public static final String ZEEBE_INDEX_PREFIX = "backup-restore-test";
  public static final String VERSION = "SNAPSHOT";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Long BACKUP_ID = 123L;
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreTest.class);
  private static final String TASKLIST_TEST_DOCKER_IMAGE = "camunda/tasklist";

  @Autowired private TasklistAPICaller tasklistAPICaller;

  @Autowired private BackupRestoreDataGenerator backupRestoreDataGenerator;

  private GenericContainer tasklistContainer;

  private ZeebeClient zeebeClient;

  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
  private BackupRestoreTestContext testContext;
  private List<String> snapshots;

  @BeforeEach
  public void setup() {
    testContext = new BackupRestoreTestContext().setZeebeIndexPrefix(ZEEBE_INDEX_PREFIX);
  }

  @Test
  public void testBackupRestore() throws Exception {
    startAllApps();
    backupRestoreDataGenerator.createData(testContext);
    backupRestoreDataGenerator.assertData();
    snapshots = createBackup();
    backupRestoreDataGenerator.changeData(testContext);
    backupRestoreDataGenerator.assertDataAfterChange();
    stopTasklist();
    deleteTasklistIndices();
    restoreBackup();
    startTasklist();
    backupRestoreDataGenerator.assertData();
  }

  public List<String> createBackup() {
    final TakeBackupResponseDto backupResponse = tasklistAPICaller.backup(BACKUP_ID);
    tasklistAPICaller.assertBackupState();
    return backupResponse.getScheduledSnapshots();
  }

  private void startAllApps() throws IOException {
    if (TasklistPropertiesUtil.isOpenSearchDatabase()) {
      startOsApps();
    } else {
      startElsApps();
    }

    tasklistContainer =
        testContainerUtil
            .createTasklistContainer(TASKLIST_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withEnv("CAMUNDA_TASKLIST_BACKUP_REPOSITORYNAME", REPOSITORY_NAME)
            .withEnv(
                "CAMUNDA_TASKLIST_DATABASE",
                TasklistPropertiesUtil.isOpenSearchDatabase() ? "opensearch" : "elasticsearch")
            .withEnv("CAMUNDA_TASKLIST_CSRF_PREVENTION_ENABLED", "false");

    startTasklist();
  }

  private void startElsApps() throws IOException {
    testContainerUtil.startElasticsearch(testContext);
    testContainerUtil.checkElasctisearchHealth(testContext);
    testContext.setEsClient(
        new RestHighLevelClient(
            RestClient.builder(
                new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))));
    createElsSnapshotRepository(testContext);

    testContainerUtil.startZeebe(
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME), testContext);
    createZeebeClient(testContext.getExternalZeebeContactPoint());
  }

  private OpenSearchClient createOsClient() {
    final org.apache.hc.core5.http.HttpHost host =
        new org.apache.hc.core5.http.HttpHost(
            testContext.getExternalOsHost(), testContext.getExternalOsPort());
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(CommonUtils.OBJECT_MAPPER);
    builder.setMapper(jsonpMapper);

    return new OpenSearchClient(builder.build());
  }

  private void startOsApps() throws IOException {
    testContainerUtil.startOpenSearch(testContext);
    final OpenSearchClient osClient = createOsClient();
    testContainerUtil.checkOpenSearchHealth(osClient);
    testContext.setOsClient(osClient);
    createOsSnapshotRepository(testContext);

    testContainerUtil.startZeebe(
        ZeebeClient.class.getPackage().getImplementationVersion(), testContext);
    createZeebeClient(testContext.getExternalZeebeContactPoint());
  }

  private void startTasklist() {
    testContainerUtil.startTasklistContainer(tasklistContainer, testContext);
    LOGGER.info("************ Tasklist started  ************");
    testContext.setTasklistRestClient(tasklistAPICaller.createGraphQLTestTemplate(testContext));
  }

  private void stopTasklist() {
    tasklistContainer.stop();
    LOGGER.info("************ Tasklist stopped  ************");
  }

  private void restoreBackup() {
    if (TasklistPropertiesUtil.isOpenSearchDatabase()) {
      restoreOsBackup();
    } else {
      restoreElsBackup();
    }
  }

  private void restoreElsBackup() {
    snapshots.forEach(
        snapshot -> {
          try {
            testContext
                .getEsClient()
                .snapshot()
                .restore(
                    new RestoreSnapshotRequest(REPOSITORY_NAME, snapshot).waitForCompletion(true),
                    RequestOptions.DEFAULT);
          } catch (final IOException e) {
            throw new TasklistRuntimeException(
                "Exception occurred while restoring the backup: " + e.getMessage(), e);
          }
        });
  }

  private void restoreOsBackup() {
    snapshots.forEach(
        snapshot -> {
          try {
            testContext
                .getOsClient()
                .snapshot()
                .restore(
                    r -> r.repository(REPOSITORY_NAME).snapshot(snapshot).waitForCompletion(true));
          } catch (final IOException | OpenSearchException e) {
            throw new TasklistRuntimeException(
                "Exception occurred while restoring the backup: " + e.getMessage(), e);
          }
        });
  }

  private void deleteTasklistIndices() {
    if (TasklistPropertiesUtil.isOpenSearchDatabase()) {
      deleteOsIndices();
    } else {
      deleteElsIndices();
    }
  }

  private void deleteOsIndices() {
    try {
      testContext.getOsClient().indices().delete(dir -> dir.index("tasklist*"));
      // we need to remove Zeebe indices as otherwise Tasklist will start importing data at once and
      // we won't be able to assert the older state of data (from backup)
      testContext.getOsClient().indices().delete(dir -> dir.index(ZEEBE_INDEX_PREFIX + "*"));
      tasklistAPICaller.checkIndicesAreDeleted(testContext.getOsClient());
      LOGGER.info("************ Tasklist OpenSearch indices deleted ************");
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Exception occurred while removing Tasklist and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void deleteElsIndices() {
    try {
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest("tasklist*"), RequestOptions.DEFAULT);
      // we need to remove Zeebe indices as otherwise Tasklist will start importing data at once and
      // we won't be able to assert the older state of data (from backup)
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest(ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT);
      tasklistAPICaller.checkIndicesAreDeleted(testContext.getEsClient());
      LOGGER.info("************ Tasklist ElasticSearch indices deleted ************");
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Exception occurred while removing Tasklist and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createElsSnapshotRepository(final BackupRestoreTestContext testContext)
      throws IOException {
    testContext
        .getEsClient()
        .snapshot()
        .createRepository(
            new PutRepositoryRequest(REPOSITORY_NAME)
                .type(FsRepository.TYPE)
                .settings(asMap("location", REPOSITORY_NAME)),
            RequestOptions.DEFAULT);
  }

  private void createOsSnapshotRepository(final BackupRestoreTestContext testContext)
      throws IOException {
    testContext
        .getOsClient()
        .snapshot()
        .createRepository(
            CreateRepositoryRequest.of(
                r ->
                    r.name(REPOSITORY_NAME)
                        .type(FsRepository.TYPE)
                        .settings(s -> s.location(REPOSITORY_NAME))));
  }

  private ZeebeClient createZeebeClient(final String zeebeGateway) {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeGateway)
            .defaultJobWorkerMaxJobsActive(5)
            .usePlaintext();
    zeebeClient = builder.build();
    return zeebeClient;
  }
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.tasklist.qa.backup",
      "io.camunda.tasklist.webapp.graphql.entity",
      "io.camunda.tasklist.qa.util.rest"
    })
class TestConfig {}
