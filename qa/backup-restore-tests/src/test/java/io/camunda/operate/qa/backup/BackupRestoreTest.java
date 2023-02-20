/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.backup;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
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
@ContextConfiguration(classes = { TestConfig.class })
public class BackupRestoreTest {

  private static final Logger logger = LoggerFactory.getLogger(BackupRestoreTest.class);

  public static final String ZEEBE_INDEX_PREFIX = "backup-restore-test";
  private static final String OPERATE_TEST_DOCKER_IMAGE = "camunda/operate";
  public static final String VERSION = "SNAPSHOT";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Integer BACKUP_ID = 123;

  @Autowired
  private OperateAPICaller operateAPICaller;

  @Autowired
  private DataGenerator dataGenerator;

  private GenericContainer operateContainer;

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
    createBackup();
    dataGenerator.changeData();
    dataGenerator.assertDataAfterChange();
    stopOperate();
    deleteOperateIndices();
    restoreBackup();
    startOperate();
    dataGenerator.assertData();
  }

  private void deleteOperateIndices() throws Exception {
    try {
      testContext.getEsClient().indices().delete(new DeleteIndexRequest("operate*"), RequestOptions.DEFAULT);
      //we need to remove Zeebe indices as otherwise Operate will start importing data at once and we won't be able to assert the older state of data (from backup)
      testContext.getEsClient().indices().delete(new DeleteIndexRequest(ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT);
      RetryOperation.newBuilder()
          .noOfRetry(10)
          .delayInterval(2000, TimeUnit.MILLISECONDS)
          .retryPredicate(result -> !(boolean)result)
          .retryConsumer(() -> !testContext.getEsClient().indices()
                .exists(new GetIndexRequest("operate*", ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT)
          )
          .build()
          .retry();
      logger.info("************ Operate indices deleted ************");
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred while removing Operate and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createBackup() throws Exception {
    TakeBackupResponseDto backupResponse = operateAPICaller.backup(BACKUP_ID);
    snapshots = backupResponse.getScheduledSnapshots();

    RetryOperation.newBuilder()
        .noOfRetry(10)
        .delayInterval(2000, TimeUnit.MILLISECONDS)
        .retryPredicate(result -> !(boolean)result)
        .retryConsumer(() -> {
          GetBackupStateResponseDto backupState = operateAPICaller.getBackupState(BACKUP_ID);
          assertThat(backupState.getState()).isIn(IN_PROGRESS, COMPLETED);
          return backupState.getState().equals(COMPLETED);
        })
        .build()
        .retry();
    logger.info("************ Backup created ************");
  }

  private void startAllApps() throws IOException {
    testContainerUtil.startElasticsearch(testContext);
    testContainerUtil.checkElasctisearchHealth(testContext);
    testContext.setEsClient(new RestHighLevelClient(
        RestClient.builder(new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))));
    createSnapshotRepository(testContext);

    String zeebeVersion = ZeebeClient.class.getPackage().getImplementationVersion();
    if (zeebeVersion.toLowerCase().contains("snapshot")) {
      zeebeVersion = "SNAPSHOT";
    }
    testContainerUtil.startZeebe(zeebeVersion, testContext);

    operateContainer = testContainerUtil.createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
        .withLogConsumer(new Slf4jLogConsumer(logger));
    operateContainer.withEnv("CAMUNDA_OPERATE_BACKUP_REPOSITORYNAME", REPOSITORY_NAME);

    startOperate();

  }

  private void startOperate() {
    logger.info("************ Starting Operate ************");
    testContainerUtil.startOperateContainer(operateContainer, testContext);
    logger.info("************ Operate started  ************");
    testContext.setOperateRestClient(operateAPICaller.createRestTemplate(testContext));
  }

  private void stopOperate() {
    operateContainer.stop();
    logger.info("************ Operate stopped  ************");
  }

  private void restoreBackup() {
    snapshots.stream().forEach(snapshot -> {
      try {
        testContext.getEsClient().snapshot().restore(new RestoreSnapshotRequest(REPOSITORY_NAME, snapshot).waitForCompletion(true), RequestOptions.DEFAULT);
      } catch (IOException e) {
        throw new OperateRuntimeException("Exception occurred while restoring the backup: " + e.getMessage(), e);
      }
    });
    logger.info("************ Backup restored ************");
  }

  private void createSnapshotRepository(BackupRestoreTestContext testContext) throws IOException {
    testContext.getEsClient().snapshot().createRepository(
        new PutRepositoryRequest(REPOSITORY_NAME).type(FsRepository.TYPE).settings(asMap("location", REPOSITORY_NAME)),
        RequestOptions.DEFAULT);
  }

}

@Configuration
@ComponentScan(basePackages = {
    "io.camunda.operate.util.rest",
    "io.camunda.operate.qa.backup",
    "io.camunda.operate.webapp.rest.dto"})
class TestConfig {

}
