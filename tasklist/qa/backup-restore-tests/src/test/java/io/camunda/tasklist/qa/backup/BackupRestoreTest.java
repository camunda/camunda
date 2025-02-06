/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME;
import static io.camunda.tasklist.util.CollectionUtil.asMap;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.backup.generator.BackupRestoreDataGenerator;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.webapps.backup.TakeBackupResponseDto;
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

  private CamundaClient camundaClient;

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
    if (TestUtil.isOpenSearch()) {
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
                TestUtil.isOpenSearch() ? "opensearch" : "elasticsearch")
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
    createCamundaClient(testContext.getExternalZeebeContactPoint());
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
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME), testContext);
    createCamundaClient(testContext.getExternalZeebeContactPoint());
  }

  private void startTasklist() {
    testContainerUtil.startTasklistContainer(tasklistContainer, VERSION, testContext);
    LOGGER.info("************ Tasklist started  ************");
    tasklistAPICaller.createRestContext(testContext);
  }

  private void stopTasklist() {
    tasklistContainer.stop();
    LOGGER.info("************ Tasklist stopped  ************");
  }

  private void restoreBackup() {
    if (TestUtil.isOpenSearch()) {
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
    if (TestUtil.isOpenSearch()) {
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

  private CamundaClient createCamundaClient(final String zeebeGateway) {
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .gatewayAddress(zeebeGateway)
            .defaultJobWorkerMaxJobsActive(5)
            .usePlaintext();
    camundaClient = builder.build();
    return camundaClient;
  }
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.tasklist.qa.backup",
      "io.camunda.tasklist.webapp.dto",
      "io.camunda.tasklist.qa.util.rest"
    })
class TestConfig {}
