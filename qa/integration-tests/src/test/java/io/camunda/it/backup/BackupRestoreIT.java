/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.qa.util.cluster.TestRestManagementClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.Either;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
public class BackupRestoreIT {
  private static final Logger LOG = LoggerFactory.getLogger(BackupRestoreIT.class);
  private static final String REPOSITORY_NAME = "test-repository";
  private static final String PROCESS_ID = "backup-process";
  private static final int PROCESS_INSTANCE_NUMBER = 1;

  @TempDir public Path repositoryDir;

  @TestZeebe(autoStart = false)
  protected TestStandaloneCamunda testStandaloneCamunda;

  @AutoClose protected BackupDBClient backupDbClient;

  private BackupRestoreTestConfig config;
  // Clients that are started after TestStandaloneCamunda
  @AutoClose private CamundaClient camundaClient;
  @AutoClose private TestRestManagementClient backupClient;
  @AutoClose private DataGenerator generator;

  @BeforeEach
  public void setup() {}

  @ParameterizedTest
  @MethodSource(value = {"sources"})
  public void shouldBackupAndRestoreToPreviousState(final BackupRestoreTestConfig config)
      throws Exception {
    // given
    setupCamunda(config);

    generator.generate(PROCESS_INSTANCE_NUMBER);

    // a backup is requested
    final var takeResponse = backupClient.takeBackup(1L);
    final var snapshots = assertNotEmptySnapshots(takeResponse);
    waitForCompletedBackup();
    LOG.info("Backup completed");

    // stop all apps and restart elasticsearch
    testStandaloneCamunda.stopApplications();
    camundaClient.close();

    // Start ES/OS before TestStandaloneCamunda in order to perform the restore
    backupDbClient.deleteAllIndices();
    Awaitility.await().untilAsserted(() -> assertThat(backupDbClient.cat()).isEmpty());

    // when
    backupDbClient.restore(REPOSITORY_NAME, snapshots);
    LOG.info("Restore completed");
    testStandaloneCamunda.start();
    testStandaloneCamunda.awaitCompleteTopology();
    initClients();

    // then
    generator.verifyAllExported();
  }

  private void setupCamunda(final BackupRestoreTestConfig config) throws Exception {
    testStandaloneCamunda = new TestStandaloneCamunda(config.databaseType);
    this.config = config;
    config.configure(testStandaloneCamunda, repositoryDir);
    testStandaloneCamunda.start();
    testStandaloneCamunda.awaitCompleteTopology();
    createBackupDbClient();
    backupDbClient.createRepository(REPOSITORY_NAME);
    initClients();
  }

  // init all clients after a new camunda is started
  private void initClients() {
    camundaClient = testStandaloneCamunda.newClientBuilder().build();
    backupClient = testStandaloneCamunda.newBackupClient();
    if (generator != null) {
      // keep the same generator as it contains the state of the process instance that were started
      generator.setCamundaClient(camundaClient);
    } else {
      generator = new DataGenerator(camundaClient, PROCESS_ID);
    }
  }

  public static Stream<BackupRestoreTestConfig> sources() {
    return Stream.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH)
        .flatMap(
            dbType ->
                Stream.of(RepositoryType.values())
                    .map(
                        repositoryType ->
                            new BackupRestoreTestConfig(dbType, repositoryType, "bucket"))
                    .limit(1));
  }

  private List<String> assertNotEmptySnapshots(
      final Either<Exception, TakeBackupHistoryResponse> response) {
    assertThat(response)
        .satisfies(Either::isLeft)
        .extracting(r -> r.get().getScheduledSnapshots())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();

    return response.get().getScheduledSnapshots();
  }

  private void waitForCompletedBackup() {
    // the backup is completed
    Awaitility.await("Backup completed")
        .atMost(Duration.ofSeconds(600))
        .until(
            () -> {
              final var backupResponse = backupClient.getBackup(1L);
              return backupResponse.isRight()
                  && backupResponse.get().getState() == BackupStateDto.COMPLETED
                  && backupResponse.get().getDetails().stream()
                      .allMatch(d -> d.getState().equals("SUCCESS"));
            });
  }

  private void createBackupDbClient() throws Exception {
    if (backupDbClient != null) {
      backupDbClient.close();
    }
    backupDbClient = BackupDBClient.create(testStandaloneCamunda, config.databaseType);
  }

  public record BackupRestoreTestConfig(
      DatabaseType databaseType, RepositoryType repositoryType, String bucket) {
    public void configure(
        final TestStandaloneCamunda testStandaloneCamunda, final Path repositoryDir) {
      testStandaloneCamunda.withBackupRepository(REPOSITORY_NAME);

      switch (repositoryType) {
        case FS -> testStandaloneCamunda.withDBContainer(c -> c.withEnv("path.repo", "~/"));
        default ->
            throw new IllegalStateException("Unsupported repository type: " + repositoryType);
      }
    }
  }

  enum RepositoryType {
    FS,
    S3,
    AZURE,
    GCS
  }
}
