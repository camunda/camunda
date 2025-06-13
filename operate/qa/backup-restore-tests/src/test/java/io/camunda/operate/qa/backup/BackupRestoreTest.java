/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
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
@ContextConfiguration(classes = {TestConfig.class})
public class BackupRestoreTest {

  public static final String ZEEBE_INDEX_PREFIX = "backup-restore-test";
  public static final String VERSION = "current-test";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Long BACKUP_ID = 123L;
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreTest.class);
  private static final String OPERATE_TEST_DOCKER_IMAGE = "localhost:5000/camunda/operate";
  @Autowired private OperateAPICaller operateAPICaller;

  @Autowired private DataGenerator dataGenerator;

  private GenericContainer operateContainer;

  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
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
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest("operate*"), RequestOptions.DEFAULT);
      // we need to remove Zeebe indices as otherwise Operate will start importing data at once and
      // we won't be able to assert the older state of data (from backup)
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest(ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT);
      RetryOperation.newBuilder()
          .noOfRetry(10)
          .delayInterval(2000, TimeUnit.MILLISECONDS)
          .retryPredicate(result -> !(boolean) result)
          .retryConsumer(
              () ->
                  !testContext
                      .getEsClient()
                      .indices()
                      .exists(
                          new GetIndexRequest("operate*", ZEEBE_INDEX_PREFIX + "*"),
                          RequestOptions.DEFAULT))
          .build()
          .retry();
      LOGGER.info("************ Operate indices deleted ************");
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred while removing Operate and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createBackup() throws Exception {
    final TakeBackupResponseDto backupResponse = operateAPICaller.backup(BACKUP_ID);
    snapshots = backupResponse.getScheduledSnapshots();

    RetryOperation.newBuilder()
        .noOfRetry(100)
        .delayInterval(
            10,
            TimeUnit.MILLISECONDS) // short delay to verify that INCOMPLETE state is not returned
        .retryPredicate(result -> !(boolean) result)
        .retryConsumer(
            () -> {
              final GetBackupStateResponseDto backupState =
                  operateAPICaller.getBackupState(BACKUP_ID);
              assertThat(backupState.getState()).isIn(IN_PROGRESS, COMPLETED);
              return backupState.getState().equals(COMPLETED);
            })
        .build()
        .retry();
    LOGGER.info("************ Backup created ************");
  }

  private void startAllApps() throws IOException {
    testContainerUtil.startElasticsearch(testContext);
    testContainerUtil.checkElasctisearchHealth(testContext);
    testContext.setEsClient(
        new RestHighLevelClient(
            RestClient.builder(
                new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))));
    createSnapshotRepository(testContext);

    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    testContainerUtil.startZeebe(zeebeVersion, testContext);

    operateContainer =
        testContainerUtil
            .createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    operateContainer.withEnv("CAMUNDA_OPERATE_BACKUP_REPOSITORYNAME", REPOSITORY_NAME);

    startOperate();
  }

  private void startOperate() {
    LOGGER.info("************ Starting Operate ************");
    testContainerUtil.startOperateContainer(operateContainer, testContext);
    LOGGER.info("************ Operate started  ************");
    testContext.setOperateRestClient(operateAPICaller.createRestTemplate(testContext));
  }

  private void stopOperate() {
    operateContainer.stop();
    LOGGER.info("************ Operate stopped  ************");
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
              } catch (final IOException e) {
                throw new OperateRuntimeException(
                    "Exception occurred while restoring the backup: " + e.getMessage(), e);
              }
            });
    LOGGER.info("************ Backup restored ************");
  }

  private void createSnapshotRepository(final BackupRestoreTestContext testContext)
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
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.operate.util.rest",
      "io.camunda.operate.qa.backup",
      "io.camunda.operate.webapp.rest.dto"
    })
class TestConfig {}
