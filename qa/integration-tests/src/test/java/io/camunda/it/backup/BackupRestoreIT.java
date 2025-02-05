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
import io.camunda.it.utils.MultiDbConfigurator;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.qa.util.cluster.HistoryBackupClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

@ZeebeIntegration
public class BackupRestoreIT {
  private static final String REPOSITORY_NAME = "test-repository";
  private static final String INDEX_PREFIX = "backup-restore";
  private static final String PROCESS_ID = "backup-process";
  private static final int PROCESS_INSTANCE_NUMBER = 10;
  protected CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  protected BackupDBClient backupDbClient;
  private String dbUrl;
  private GenericContainer<?> searchContainer;
  private DataGenerator generator;
  private BackupRestoreTestConfig config;
  private HistoryBackupClient historyBackupClient;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(backupDbClient, camundaClient, generator, searchContainer);
  }

  private void setup(final BackupRestoreTestConfig config) throws Exception {
    testStandaloneApplication = new TestSimpleCamundaApplication();
    final var configurator = new MultiDbConfigurator(testStandaloneApplication);
    searchContainer =
        switch (config.databaseType) {
          case ELASTICSEARCH -> {
            final var container =
                TestSearchContainers.createDefeaultElasticsearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    // location of the repository that will be used for snapshots
                    .withEnv("path.repo", "~/");
            // container.addFileSystemBind(
            // repositoryDir.toString(), "~/", BindMode.READ_WRITE, SelinuxContext.SHARED);
            container.start();
            dbUrl = "http://" + container.getHttpHostAddress();

            // configure the app
            configurator.configureElasticsearchSupport(dbUrl, INDEX_PREFIX);
            yield container;
          }

          case OPENSEARCH -> {
            final var container =
                TestSearchContainers.createDefaultOpensearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    // location of the repository that will be used for snapshots
                    .withEnv("path.repo", "~/");
            container.start();
            dbUrl = container.getHttpHostAddress();
            configurator.configureOpenSearchSupport(dbUrl, INDEX_PREFIX, "admin", "admin");
            yield container;
          }

          default ->
              throw new IllegalArgumentException(
                  "Unsupported database type: " + config.databaseType);
        };
    configurator.getOperateProperties().getBackup().setRepositoryName(REPOSITORY_NAME);
    configurator.getTasklistProperties().getBackup().setRepositoryName(REPOSITORY_NAME);

    this.config = config;
    testStandaloneApplication.start().awaitCompleteTopology();

    camundaClient = testStandaloneApplication.newClientBuilder().build();

    historyBackupClient = HistoryBackupClient.of(testStandaloneApplication);
    backupDbClient = BackupDBClient.create(dbUrl, config.databaseType);
    backupDbClient.createRepository(REPOSITORY_NAME);
    generator = new DataGenerator(camundaClient, PROCESS_ID);
  }

  public static Stream<BackupRestoreTestConfig> sources() {
    final var backupRestoreConfigs = new ArrayList<BackupRestoreTestConfig>();
    for (final var db : List.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH)) {
      backupRestoreConfigs.add(new BackupRestoreTestConfig(db, "bucket"));
    }
    return backupRestoreConfigs.stream();
  }

  @ParameterizedTest
  @MethodSource(value = {"sources"})
  public void shouldBackupAndRestoreToPreviousState(final BackupRestoreTestConfig config)
      throws Exception {
    // given
    setup(config);
    camundaClient.newTopologyRequest().send().join();
    generator.generate(PROCESS_INSTANCE_NUMBER);

    final var takeResponse = historyBackupClient.takeBackup(1L);
    assertThat(takeResponse)
        .extracting(TakeBackupHistoryResponse::getScheduledSnapshots)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final var snapshots = takeResponse.getScheduledSnapshots();

    Awaitility.await("Backup completed")
        .atMost(Duration.ofSeconds(600))
        .untilAsserted(
            () -> {
              final var backupResponse = historyBackupClient.getBackup(1L);
              assertThat(backupResponse.getState()).isEqualTo(BackupStateDto.COMPLETED);
              assertThat(backupResponse.getDetails()).allMatch(d -> d.getState().equals("SUCCESS"));
            });

    // when
    // if we stop all apps and restart elasticsearch
    testStandaloneApplication.stop();

    backupDbClient.deleteAllIndices(INDEX_PREFIX);
    Awaitility.await().untilAsserted(() -> assertThat(backupDbClient.cat()).isEmpty());

    // restore with a new client is successful
    backupDbClient.restore(REPOSITORY_NAME, snapshots);

    testStandaloneApplication.start();

    // then
    generator.verifyAllExported();
  }

  public record BackupRestoreTestConfig(DatabaseType databaseType, String bucket) {}
}
