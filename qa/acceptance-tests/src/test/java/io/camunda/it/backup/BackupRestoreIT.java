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
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.it.backup.data.DataGenerator;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This test is on purpose no {@link io.camunda.qa.util.multidb.MultiDbTest}, as we have special
 * requirements on ES and OS, and need direct access.
 *
 * <ul>
 *   <li>We need to configure ES/OS to allow local repositories.
 *   <li>We need to create snapshot repositories with specific ES/OS clients.
 *   <li>We need to create snapshots of the Zeebe exporter indices
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
  private static final long BACKUP_ID = 3L;
  private static final int PROCESS_INSTANCE_NUMBER = 10;

  private static final Duration TIMEOUT = Duration.ofSeconds(500);
  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  private DataGenerator generator;

  @BeforeAll
  public static void beforeAll() {
    // Non-snapshot version
    VersionUtil.overrideVersionForTesting("8.10.0");
  }

  @AfterAll
  public static void afterAll() {
    VersionUtil.resetVersionForTesting();
  }

  @AfterEach
  public void tearDown() {
    super.tearDown(webappsDBClient, camundaClient, generator, searchContainer);
  }

  private void setup(final BackupRestoreTestConfig config) throws Exception {
    testStandaloneApplication =
        super.setup(
            config.databaseType,
            REPOSITORY_NAME,
            INDEX_PREFIX,
            Duration.ofSeconds(1),
            AZURITE_CONTAINER,
            EXECUTOR);
    generator = new DataGenerator(camundaClient, PROCESS_ID, TIMEOUT);
  }

  public static Stream<BackupRestoreTestConfig> sources() {
    final var backupRestoreConfigs = new ArrayList<BackupRestoreTestConfig>();
    for (final var db : supportedDatabases().toList()) {
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

    testStandaloneApplication.awaitCompleteTopology();
    // generate completed processes
    generator.generateCompletedProcesses(PROCESS_INSTANCE_NUMBER);

    // generate some processes, but do not complete them,
    // we will complete them after the restore
    generator.generateUncompletedProcesses(PROCESS_INSTANCE_NUMBER);

    // BACKUP PROCEDURE
    // Zeebe is soft-paused
    exportingActuator.softPause();

    final var snapshots = takeHistoryBackup(BACKUP_ID, "Webapps Backup completed");
    takeZeebeBackup(BACKUP_ID, REPOSITORY_NAME);
    exportingActuator.resume();

    // when
    // if we stop all apps and restart elasticsearch
    testStandaloneApplication.stop();
    // Zeebe's folder is deleted by ZeebeIntegration automatically when the application is stop()

    webappsDBClient.deleteAllIndices(INDEX_PREFIX);
    // also delete index templates, to test the case restore is done in an empty ES/OS cluster
    webappsDBClient.deleteAllIndexTemplates(INDEX_PREFIX);

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(webappsDBClient.cat(INDEX_PREFIX)).isEmpty();
              assertThat(webappsDBClient.getIndexTemplates(INDEX_PREFIX)).isEmpty();
            });

    // RESTORE PROCEDURE
    webappsDBClient.restore(REPOSITORY_NAME, snapshots);
    restoreZeebe(BACKUP_ID, AZURITE_CONTAINER);

    // Since the unified configuration does not contain the full data directory path, we set the
    // working directory so that the path can be properly constructed during application startup.
    testStandaloneApplication.withBean(
        "workingDirectory", workingDirectory, WorkingDirectory.class);

    testStandaloneApplication.start();

    generator.verifyAllExported(ProcessInstanceState.ACTIVE);
    // complete the processes that were not terminated before stopping the apps
    generator.completeProcesses();

    // then
    generator.verifyAllExported(ProcessInstanceState.COMPLETED);
    assertThat(webappsDBClient.getIndexTemplates(INDEX_PREFIX)).isNotEmpty();
  }

  public record BackupRestoreTestConfig(DatabaseType databaseType, String bucket) {}
}
