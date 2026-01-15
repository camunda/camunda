/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import static io.camunda.webapps.backup.BackupStateDto.*;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.RetryOperation;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
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

  public static final String INDEX_PREFIX = "backup-restore-test";
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
    testContext = new BackupRestoreTestContext().setIndexPrefix(INDEX_PREFIX);
    testContext.setDatabaseType(ConnectionTypes.ELASTICSEARCH.getType());
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
      testContext.getEsClient().indices().delete(d -> d.index(INDEX_PREFIX + "*"));
      RetryOperation.newBuilder()
          .noOfRetry(10)
          .delayInterval(2000, TimeUnit.MILLISECONDS)
          .retryPredicate(result -> !(boolean) result)
          .retryConsumer(
              () ->
                  !testContext
                      .getEsClient()
                      .indices()
                      .exists(e -> e.index(INDEX_PREFIX + "*"))
                      .value())
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
        .noOfRetry(10)
        .delayInterval(2000, TimeUnit.MILLISECONDS)
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
    final RestClient restClient =
        RestClient.builder(
                new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))
            .build();
    final RestClientTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());
    final ElasticsearchClient esClient = new ElasticsearchClient(transport);
    testContext.setEsClient(esClient);
    testContainerUtil.checkElasticsearchHealth(testContext, esClient);
    createSnapshotRepository(testContext);

    testContainerUtil.startZeebe(testContext);

    operateContainer =
        testContainerUtil
            .createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withEnv("CAMUNDA_DATABASE_INDEXPREFIX", INDEX_PREFIX)
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX", INDEX_PREFIX)
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", INDEX_PREFIX)
            .withEnv("CAMUNDA_OPERATE_BACKUP_REPOSITORYNAME", REPOSITORY_NAME);

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
                        r ->
                            r.repository(REPOSITORY_NAME)
                                .snapshot(snapshot)
                                .waitForCompletion(true));
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
            r ->
                r.name(REPOSITORY_NAME)
                    .repository(
                        repo -> repo.fs(f -> f.settings(s -> s.location(REPOSITORY_NAME)))));
  }
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.operate.util.rest",
      "io.camunda.operate.testhelpers",
      "io.camunda.operate.qa.backup",
      "io.camunda.operate.webapp.rest.dto"
    })
class TestConfig {}
