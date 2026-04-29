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
 *   <li>We need to create snapshots of the Zeebe exporter indices.
 * </ul>
 */
@Testcontainers
@ZeebeIntegration
public class TasklistBackupRestoreIT extends AbstractBackupRestoreIT {
  private static final String REPOSITORY_NAME = "tasklist-test-repository";
  private static final String INDEX_PREFIX = "tasklist-backup-restore";
  private static final long BACKUP_ID = 123L;
  private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(5);

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  private TasklistDataGenerator generator;

  @BeforeAll
  public static void beforeAll() {
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

  public static Stream<DatabaseType> sources() {
    return supportedDatabases();
  }

  @ParameterizedTest
  @MethodSource("sources")
  void shouldRestoreTasklistHistoryToTheBackedUpState(final DatabaseType databaseType)
      throws Exception {
    // given
    setup(databaseType);

    testStandaloneApplication.awaitCompleteTopology();
    generator.createData();
    generator.assertData();

    exportingActuator.softPause();
    final List<String> historySnapshots = takeHistoryBackup(BACKUP_ID, "Tasklist backup completed");
    takeZeebeBackup(BACKUP_ID, REPOSITORY_NAME);
    exportingActuator.resume();

    generator.changeData();
    generator.assertDataAfterChange();

    // when
    testStandaloneApplication.stop();
    webappsDBClient.deleteAllIndices(INDEX_PREFIX);

    Awaitility.await("tasklist indices should be deleted")
        .untilAsserted(() -> assertThat(webappsDBClient.cat(INDEX_PREFIX)).isEmpty());

    webappsDBClient.restore(REPOSITORY_NAME, historySnapshots);
    restoreZeebe(BACKUP_ID, AZURITE_CONTAINER);

    testStandaloneApplication.withBean(
        "workingDirectory", workingDirectory, WorkingDirectory.class);
    testStandaloneApplication.start();
    refreshCamundaClient(CLIENT_TIMEOUT, client -> generator.setCamundaClient(client));

    // then
    generator.assertData();
  }

  private void setup(final DatabaseType databaseType) throws Exception {
    testStandaloneApplication =
        super.setup(
            databaseType,
            REPOSITORY_NAME,
            INDEX_PREFIX,
            CLIENT_TIMEOUT,
            AZURITE_CONTAINER,
            EXECUTOR);
    generator = new TasklistDataGenerator(camundaClient);
  }
}
