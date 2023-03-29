/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.util.CollectionUtil.asMap;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.util.TestContainerUtil;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {TestConfig.class})
public class BackupRestoreTest {

  public static final String ZEEBE_INDEX_PREFIX = "backup-restore-test";
  public static final String VERSION = "SNAPSHOT";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Integer BACKUP_ID = 123;
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreTest.class);
  private static final String TASKLIST_TEST_DOCKER_IMAGE = "camunda/tasklist";

  @Autowired private TasklistAPICaller tasklistAPICaller;

  @Autowired private DataGenerator dataGenerator;

  private GenericContainer tasklistContainer;

  private ZeebeClient zeebeClient;

  private TestContainerUtil testContainerUtil = new TestContainerUtil();
  private BackupRestoreTestContext testContext;
  private List<String> snapshots;

  @Before
  public void setup() {
    testContext = new BackupRestoreTestContext().setZeebeIndexPrefix(ZEEBE_INDEX_PREFIX);
  }

  @Test
  public void testBackupRestore() throws Exception {
    startAllApps();
    dataGenerator.createData(testContext);
    dataGenerator.assertData();
    snapshots = createBackup();
    dataGenerator.changeData(testContext);
    dataGenerator.assertDataAfterChange();
    stopTasklist();
    deleteTasklistIndices();
    restoreBackup();
    startTasklist();
    dataGenerator.assertData();
  }

  public List<String> createBackup() {
    final TakeBackupResponseDto backupResponse = tasklistAPICaller.backup(BACKUP_ID);
    tasklistAPICaller.assertBackupState();
    return backupResponse.getScheduledSnapshots();
  }

  private void startAllApps() throws IOException {
    testContainerUtil.startElasticsearch(testContext);
    testContainerUtil.checkElasctisearchHealth(testContext);
    testContext.setEsClient(
        new RestHighLevelClient(
            RestClient.builder(
                new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))));
    createSnapshotRepository(testContext);

    testContainerUtil.startZeebe(
        ZeebeClient.class.getPackage().getImplementationVersion(), testContext);
    createZeebeClient(testContext.getExternalZeebeContactPoint());

    tasklistContainer =
        testContainerUtil
            .createTasklistContainer(TASKLIST_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    tasklistContainer.withEnv("CAMUNDA_TASKLIST_BACKUP_REPOSITORYNAME", REPOSITORY_NAME);

    startTasklist();
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
    snapshots.stream()
        .forEach(
            snapshot -> {
              try {
                testContext
                    .getEsClient()
                    .snapshot()
                    .restore(
                        new RestoreSnapshotRequest(REPOSITORY_NAME, snapshot)
                            .waitForCompletion(true),
                        RequestOptions.DEFAULT);
              } catch (IOException e) {
                throw new TasklistRuntimeException(
                    "Exception occurred while restoring the backup: " + e.getMessage(), e);
              }
            });
  }

  private void deleteTasklistIndices() throws Exception {
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
      LOGGER.info("************ Tasklist indices deleted ************");
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          "Exception occurred while removing Tasklist and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createSnapshotRepository(BackupRestoreTestContext testContext) throws IOException {
    testContext
        .getEsClient()
        .snapshot()
        .createRepository(
            new PutRepositoryRequest(REPOSITORY_NAME)
                .type(FsRepository.TYPE)
                .settings(asMap("location", REPOSITORY_NAME)),
            RequestOptions.DEFAULT);
  }

  private ZeebeClient createZeebeClient(String zeebeGateway) {
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
