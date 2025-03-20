/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import feign.FeignException.NotFound;
import io.camunda.application.commons.backup.HistoryBackupComponent;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstanceState;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.qa.util.cluster.HistoryBackupClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.schema.descriptors.backup.SnapshotIndexCollection;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ExportingActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
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
public class BackupRestoreIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreIT.class);
  private static final String REPOSITORY_NAME = "test-repository";
  private static final String INDEX_PREFIX = "backup-restore";
  private static final String PROCESS_ID = "backup-process";
  private static final long BACKUP_ID = 3L;
  private static final int PROCESS_INSTANCE_NUMBER = 10;

  private static final Duration TIMEOUT = Duration.ofSeconds(500);
  private static final SnapshotNameProvider SNAPSHOT_NAME_PROVIDER =
      new ZeebeSnapshotNameProvider();
  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
  protected CamundaClient camundaClient;
  protected ExportingActuator exportingActuator;
  protected BackupActuator backupActuator;

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  protected BackupDBClient webappsDBClient;

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  private final String containerName =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
  // cannot be a @Container because it's initialized in setup()
  private GenericContainer<?> searchContainer;
  private DataGenerator generator;
  private HistoryBackupClient historyBackupClient;
  private MultiDbConfigurator configurator;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(webappsDBClient, camundaClient, generator, searchContainer);
  }

  private void setup(final BackupRestoreTestConfig config) throws Exception {
    testStandaloneApplication = new TestSimpleCamundaApplication().withUnauthenticatedAccess();
    configurator = new MultiDbConfigurator(testStandaloneApplication);
    testStandaloneApplication.withBrokerConfig(this::configureZeebeBackupStore);
    final String dbUrl;
    searchContainer =
        switch (config.databaseType) {
          case ELASTICSEARCH -> {
            final var container =
                TestSearchContainers.createDefeaultElasticsearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    // location of the repository that will be used for snapshots
                    .withEnv("path.repo", "~/");
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

    testStandaloneApplication.withProperty(
        "camunda.tasklist.backup.repositoryName", REPOSITORY_NAME);
    testStandaloneApplication.withProperty(
        "camunda.operate.backup.repositoryName", REPOSITORY_NAME);

    testStandaloneApplication.start().awaitCompleteTopology();

    camundaClient =
        testStandaloneApplication
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(1))
            .build();
    exportingActuator = ExportingActuator.of(testStandaloneApplication);
    backupActuator = BackupActuator.of(testStandaloneApplication);

    historyBackupClient = HistoryBackupClient.of(testStandaloneApplication);
    webappsDBClient = BackupDBClient.create(dbUrl, config.databaseType, EXECUTOR);
    webappsDBClient.createRepository(REPOSITORY_NAME);
    generator = new DataGenerator(camundaClient, PROCESS_ID, TIMEOUT);
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

    testStandaloneApplication.awaitCompleteTopology();
    // generate completed processes
    generator.generateCompletedProcesses(PROCESS_INSTANCE_NUMBER);

    // generate some processes, but do not complete them,
    // we will complete them after the restore
    generator.generateUncompletedProcesses(PROCESS_INSTANCE_NUMBER);

    // BACKUP PROCEDURE
    // Zeebe is soft-paused
    exportingActuator.softPause();

    final var snapshots = takeHistoryBackup();
    takeZeebeBackup();
    exportingActuator.resume();

    // when
    // closing down backup's thread poll before shutting down testStandaloneApplication
    testStandaloneApplication.bean(HistoryBackupComponent.class).shutdownExecutor();
    // if we stop all apps and restart elasticsearch
    testStandaloneApplication.stop();
    // Zeebe's folder is deleted by ZeebeIntegration automatically when the application is stop()

    webappsDBClient.deleteAllIndices(INDEX_PREFIX);

    Awaitility.await().untilAsserted(() -> assertThat(webappsDBClient.cat()).isEmpty());

    // RESTORE PROCEDURE
    webappsDBClient.restore(REPOSITORY_NAME, snapshots);
    restoreZeebe();

    testStandaloneApplication.start();

    generator.verifyAllExported(ProcessInstanceState.ACTIVE);
    // complete the processes that were not terminated before stopping the apps
    generator.completeProcesses();

    // then
    generator.verifyAllExported(ProcessInstanceState.COMPLETED);
  }

  private void configureZeebeBackupStore(final BrokerCfg cfg) {
    final var backup = cfg.getData().getBackup();
    final var azure = backup.getAzure();

    backup.setStore(BackupStoreType.AZURE);
    azure.setBasePath(containerName);
    azure.setConnectionString(AZURITE_CONTAINER.getConnectString());
  }

  private void restoreZeebe() {
    try (final var restoreApp =
        new TestRestoreApp(testStandaloneApplication.brokerConfig()).withBackupId(BACKUP_ID)) {
      assertThatNoException().isThrownBy(restoreApp::start);
    }
  }

  private List<String> takeHistoryBackup() {
    final var takeResponse = historyBackupClient.takeBackup(BACKUP_ID);
    assertThat(takeResponse)
        .extracting(TakeBackupHistoryResponse::getScheduledSnapshots)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final var snapshots = takeResponse.getScheduledSnapshots();

    Awaitility.await("Webapps Backup completed")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              try {
                final var backupResponse = historyBackupClient.getBackup(BACKUP_ID);
                assertThat(backupResponse.getState()).isEqualTo(BackupStateDto.COMPLETED);
                assertThat(backupResponse.getDetails())
                    .allMatch(d -> d.getState().equals("SUCCESS"));
              } catch (final NotFound e) {
                throw new AssertionError("Backup not found:", e);
              }
            });
    return snapshots;
  }

  private void takeZeebeBackup() {
    final var metadata = new Metadata(BACKUP_ID, "current", 1, 1);
    final List<String> indices;
    try {
      indices =
          webappsDBClient.cat().stream()
              .filter(name -> name.startsWith(configurator.zeebeIndexPrefix()))
              .toList();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    assertThat(indices).isNotEmpty();

    final var zeebeIndicesBackupStatus = new AtomicReference<String>();

    final var backupRepository =
        webappsDBClient.zeebeBackupRepository(REPOSITORY_NAME, SNAPSHOT_NAME_PROVIDER);

    backupRepository.executeSnapshotting(
        new SnapshotRequest(
            REPOSITORY_NAME,
            SNAPSHOT_NAME_PROVIDER.getSnapshotName(metadata),
            new SnapshotIndexCollection(indices, List.of()),
            metadata),
        () -> {
          zeebeIndicesBackupStatus.set("COMPLETED");
          LOGGER.info("Backup of zeebe ES/OS records completed");
        },
        () -> {
          zeebeIndicesBackupStatus.set("FAILURE");
          LOGGER.error("Failed to take backup of zeeebe ES/OS records");
        });
    Awaitility.await("zeebe indices have been backed up")
        .untilAsserted(() -> assertThat(zeebeIndicesBackupStatus.get()).isNotNull());
    assertThat(zeebeIndicesBackupStatus.get()).isEqualTo("COMPLETED");

    backupActuator.take(BACKUP_ID);
    Awaitility.await("Zeebe backup completed")
        .untilAsserted(
            () -> {
              try {
                final var status = backupActuator.status(BACKUP_ID);
                assertThat(status.getState()).isEqualTo(StateCode.COMPLETED);
                assertThat(status.getDetails())
                    .allSatisfy(d -> assertThat(d.getState()).isEqualTo(StateCode.COMPLETED));
              } catch (final Exception e) {
                throw new AssertionError("Backup not found", e);
              }
            });
  }

  public record BackupRestoreTestConfig(DatabaseType databaseType, String bucket) {}
}
