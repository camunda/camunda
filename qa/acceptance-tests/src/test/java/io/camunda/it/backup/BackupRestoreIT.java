/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.it.backup.data.DataGenerator;
import io.camunda.it.backup.data.OperateDataGenerator;
import io.camunda.it.backup.data.TasklistDataGenerator;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end backup and restore test covering Zeebe, Operate, and Tasklist data in a single
 * backup/restore cycle. Runs against all supported search backends (Elasticsearch and OpenSearch).
 *
 * <p>This test is on purpose no {@link io.camunda.qa.util.multidb.MultiDbTest}, as we have special
 * requirements on ES and OS, and need direct access.
 *
 * <ul>
 *   <li>We need to configure ES/OS to allow local repositories.
 *   <li>We need to create snapshot repositories with specific ES/OS clients.
 *   <li>We need to create snapshots of the Zeebe exporter indices.
 * </ul>
 *
 * Furthermore, this test will not apply to RDBMS.
 */
@Testcontainers
@ZeebeIntegration
public class BackupRestoreIT extends AbstractBackupRestoreIT {

  private static final String REPOSITORY_NAME = "test-repository";
  private static final String INDEX_PREFIX = "backup-restore";
  private static final String PROCESS_ID = "backup-process";
  private static final long BACKUP_ID = 1L;
  private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration GENERATOR_TIMEOUT = Duration.ofSeconds(500);

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  @BeforeAll
  public static void beforeAll() {
    // Non-snapshot version
    VersionUtil.overridePrerelease();
  }

  @AfterAll
  public static void afterAll() {
    VersionUtil.resetVersionForTesting();
  }

  @ParameterizedTest
  @MethodSource("supportedDatabases")
  void shouldBackupAndRestoreAllData(final DatabaseType databaseType) throws Exception {
    runBackupRestoreForDatabase(databaseType);
  }

  private void runBackupRestoreForDatabase(final DatabaseType databaseType) throws Exception {
    testStandaloneApplication =
        super.setup(
            databaseType,
            REPOSITORY_NAME,
            INDEX_PREFIX,
            CLIENT_TIMEOUT,
            AZURITE_CONTAINER,
            EXECUTOR);

    final var operateGenerator = new OperateDataGenerator(camundaClient);
    final var tasklistGenerator = new TasklistDataGenerator(camundaClient);
    final var dataGenerator = new DataGenerator(camundaClient, PROCESS_ID, GENERATOR_TIMEOUT);

    try {
      testStandaloneApplication.awaitCompleteTopology();

      // given: create initial data for all components
      operateGenerator.createData();
      operateGenerator.assertData();
      tasklistGenerator.createData();
      tasklistGenerator.assertData();
      dataGenerator.createData();

      // backup
      exportingActuator.softPause();
      final List<String> snapshots =
          takeHistoryBackup(BACKUP_ID, "All components backup completed");
      takeZeebeBackup(BACKUP_ID, REPOSITORY_NAME);
      exportingActuator.resume();

      // when: mutate state after the backup to prove restore reverts it
      operateGenerator.changeData();
      operateGenerator.assertDataAfterChange();
      tasklistGenerator.changeData();
      tasklistGenerator.assertDataAfterChange();

      testStandaloneApplication.stop();
      webappsDBClient.deleteAllIndices(INDEX_PREFIX);
      webappsDBClient.deleteAllIndexTemplates(INDEX_PREFIX);

      Awaitility.await("indices and templates deleted")
          .untilAsserted(
              () -> {
                assertThat(webappsDBClient.cat(INDEX_PREFIX)).isEmpty();
                assertThat(webappsDBClient.getIndexTemplates(INDEX_PREFIX)).isEmpty();
              });

      webappsDBClient.restore(REPOSITORY_NAME, snapshots);
      restoreZeebe(BACKUP_ID, AZURITE_CONTAINER);

      testStandaloneApplication.withBean(
          "workingDirectory", workingDirectory, WorkingDirectory.class);
      testStandaloneApplication.start();
      refreshCamundaClient(
          CLIENT_TIMEOUT,
          client -> {
            operateGenerator.setCamundaClient(client);
            tasklistGenerator.setCamundaClient(client);
            dataGenerator.setCamundaClient(client);
          });

      // then: all components are restored to their pre-backup state
      operateGenerator.assertData();
      tasklistGenerator.assertData();
      dataGenerator.assertData();
      assertThat(webappsDBClient.getIndexTemplates(INDEX_PREFIX)).isNotEmpty();

    } finally {
      CloseHelper.quietCloseAll(
          webappsDBClient,
          camundaClient,
          operateGenerator,
          tasklistGenerator,
          dataGenerator,
          searchContainer,
          testStandaloneApplication);
    }
  }
}
