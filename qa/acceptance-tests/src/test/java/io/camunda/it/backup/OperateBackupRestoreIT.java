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
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.it.document.DocumentClient;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.qa.util.cluster.HistoryBackupClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.schema.descriptors.backup.SnapshotIndexCollection;
import io.camunda.zeebe.broker.system.configuration.ConfigurationUtil;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ExportingActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class OperateBackupRestoreIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(OperateBackupRestoreIT.class);
  private static final String REPOSITORY_NAME = "operate-test-repository";
  private static final String INDEX_PREFIX = "operate-backup-restore";
  private static final long BACKUP_ID = 123L;
  private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(5);
  private static final SnapshotNameProvider SNAPSHOT_NAME_PROVIDER =
      new ZeebeSnapshotNameProvider();

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  private final String containerName =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();

  private CamundaClient camundaClient;
  private ExportingActuator exportingActuator;
  private BackupActuator backupActuator;
  private DocumentClient webappsDbClient;
  private GenericContainer<?> searchContainer;
  private HistoryBackupClient historyBackupClient;
  private MultiDbConfigurator configurator;
  private WorkingDirectory workingDirectory;
  private OperateDataGenerator generator;

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
    CloseHelper.quietCloseAll(webappsDbClient, camundaClient, generator, searchContainer);
  }

  public static Stream<DatabaseType> sources() {
    final String configuredDatabase = System.getProperty("camunda.it.backup.databaseType");
    if (configuredDatabase != null && !configuredDatabase.isBlank()) {
      return Stream.of(DatabaseType.valueOf(configuredDatabase.toUpperCase(Locale.ROOT)));
    }
    return Stream.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH);
  }

  @ParameterizedTest
  @MethodSource("sources")
  void shouldRestoreOperateHistoryToTheBackedUpState(final DatabaseType databaseType)
      throws Exception {
    // given
    setup(databaseType);

    testStandaloneApplication.awaitCompleteTopology();
    generator.createData();
    generator.assertData();

    exportingActuator.softPause();
    final List<String> historySnapshots = takeHistoryBackup();
    takeZeebeBackup();
    exportingActuator.resume();

    generator.changeData();
    generator.assertDataAfterChange();

    // when
    testStandaloneApplication.stop();
    webappsDbClient.deleteAllIndices(INDEX_PREFIX);

    Awaitility.await("history indices should be deleted")
        .untilAsserted(() -> assertThat(webappsDbClient.cat(INDEX_PREFIX)).isEmpty());

    webappsDbClient.restore(REPOSITORY_NAME, historySnapshots);
    restoreZeebe();

    testStandaloneApplication.withBean(
        "workingDirectory", workingDirectory, WorkingDirectory.class);
    testStandaloneApplication.start();
    refreshCamundaClient();

    // then
    generator.assertData();
  }

  private void setup(final DatabaseType databaseType) throws Exception {
    testStandaloneApplication =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();
    configurator = new MultiDbConfigurator(testStandaloneApplication);
    testStandaloneApplication.withUnifiedConfig(
        cfg -> configureZeebeBackupStore(cfg, databaseType));

    final String dbUrl;
    searchContainer =
        switch (databaseType) {
          case ELASTICSEARCH -> {
            final var container =
                TestSearchContainers.createDefeaultElasticsearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    .withEnv("path.repo", "~/");
            container.start();
            dbUrl = "http://" + container.getHttpHostAddress();
            configurator.configureElasticsearchSupportIncludingOldExporter(dbUrl, INDEX_PREFIX);
            yield container;
          }
          case OPENSEARCH -> {
            final var container =
                TestSearchContainers.createDefaultOpensearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    .withEnv("path.repo", "~/");
            container.start();
            dbUrl = container.getHttpHostAddress();
            configurator.configureOpenSearchSupportIncludingOldExporter(
                dbUrl, INDEX_PREFIX, "admin", "admin");
            yield container;
          }
          default ->
              throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        };

    configureZeebeBackupStore(testStandaloneApplication.unifiedConfig(), databaseType);

    testStandaloneApplication.start().awaitCompleteTopology();

    refreshCamundaClient();
    exportingActuator = ExportingActuator.of(testStandaloneApplication);
    backupActuator = BackupActuator.of(testStandaloneApplication);
    historyBackupClient = HistoryBackupClient.of(testStandaloneApplication);
    webappsDbClient = DocumentClient.create(dbUrl, databaseType, EXECUTOR);
    webappsDbClient.createRepository(REPOSITORY_NAME);
    generator = new OperateDataGenerator(camundaClient, webappsDbClient, INDEX_PREFIX);
    workingDirectory = testStandaloneApplication.bean(WorkingDirectory.class);
  }

  private void refreshCamundaClient() {
    CloseHelper.quietClose(camundaClient);
    camundaClient =
        testStandaloneApplication.newClientBuilder().defaultRequestTimeout(CLIENT_TIMEOUT).build();
    if (generator != null) {
      generator.setCamundaClient(camundaClient);
    }
  }

  private void configureZeebeBackupStore(final Camunda cfg, final DatabaseType databaseType) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    final var azure = backup.getAzure();

    backup.setStore(BackupStoreType.AZURE);
    azure.setBasePath(containerName);
    azure.setConnectionString(AZURITE_CONTAINER.getConnectString());

    if (databaseType.isElasticSearch()) {
      cfg.getData()
          .getSecondaryStorage()
          .getElasticsearch()
          .getBackup()
          .setRepositoryName(REPOSITORY_NAME);
      cfg.getData()
          .getSecondaryStorage()
          .getElasticsearch()
          .getHistory()
          .setWaitPeriodBeforeArchiving("0s");
      cfg.getData()
          .getSecondaryStorage()
          .getElasticsearch()
          .getHistory()
          .setDelayBetweenRuns(Duration.ofSeconds(1));
      cfg.getData()
          .getSecondaryStorage()
          .getElasticsearch()
          .getHistory()
          .setMaxDelayBetweenRuns(Duration.ofSeconds(1));
    } else {
      cfg.getData()
          .getSecondaryStorage()
          .getOpensearch()
          .getBackup()
          .setRepositoryName(REPOSITORY_NAME);
      cfg.getData()
          .getSecondaryStorage()
          .getOpensearch()
          .getHistory()
          .setWaitPeriodBeforeArchiving("0s");
      cfg.getData()
          .getSecondaryStorage()
          .getOpensearch()
          .getHistory()
          .setDelayBetweenRuns(Duration.ofSeconds(1));
      cfg.getData()
          .getSecondaryStorage()
          .getOpensearch()
          .getHistory()
          .setMaxDelayBetweenRuns(Duration.ofSeconds(1));
    }
  }

  private void restoreZeebe() {
    final var unifiedRestoreConfig = new Camunda();
    final var backup = unifiedRestoreConfig.getData().getPrimaryStorage().getBackup();

    backup.setStore(PrimaryStorageBackup.BackupStoreType.AZURE);
    backup.getAzure().setBasePath(containerName);
    backup.getAzure().setConnectionString(AZURITE_CONTAINER.getConnectString());

    final var brokerCfg = testStandaloneApplication.unifiedConfig();
    unifiedRestoreConfig.getCluster().setNodeId(brokerCfg.getCluster().getNodeId());
    unifiedRestoreConfig.getCluster().setPartitionCount(brokerCfg.getCluster().getPartitionCount());
    unifiedRestoreConfig
        .getData()
        .getPrimaryStorage()
        .setDirectory(
            ConfigurationUtil.toAbsolutePath(
                brokerCfg.getData().getPrimaryStorage().getDirectory(),
                workingDirectory.path().toString()));
    unifiedRestoreConfig.getCluster().setSize(brokerCfg.getCluster().getSize());

    try (final var restoreApp = new TestRestoreApp(unifiedRestoreConfig).withBackupId(BACKUP_ID)) {
      assertThatNoException().isThrownBy(restoreApp::start);
    }
  }

  private List<String> takeHistoryBackup() {
    final var takeResponse = historyBackupClient.takeBackup(BACKUP_ID);
    assertThat(takeResponse)
        .extracting(TakeBackupHistoryResponse::getScheduledSnapshots)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final List<String> snapshots = takeResponse.getScheduledSnapshots();

    Awaitility.await("Webapps backup completed")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              try {
                final var backupResponse = historyBackupClient.getBackup(BACKUP_ID);
                assertThat(backupResponse.getState()).isEqualTo(BackupStateDto.COMPLETED);
                assertThat(backupResponse.getDetails())
                    .allMatch(detail -> detail.getState().equals("SUCCESS"));
              } catch (final NotFound e) {
                throw new AssertionError("Backup not found", e);
              }
            });

    for (final boolean verbose : List.of(true, false)) {
      assertThat(historyBackupClient.getBackups(verbose, BACKUP_ID + "*"))
          .allSatisfy(state -> assertThat(state.getState()).isEqualTo(BackupStateDto.COMPLETED));
    }

    return snapshots;
  }

  private void takeZeebeBackup() {
    final var metadata = new Metadata(BACKUP_ID, "current", 1, 1);
    final List<String> indices;
    try {
      indices = webappsDbClient.cat(configurator.zeebeIndexPrefix());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    assertThat(indices).isNotEmpty();

    final var zeebeIndicesBackupStatus = new AtomicReference<String>();
    final var backupRepository =
        webappsDbClient.zeebeBackupRepository(REPOSITORY_NAME, SNAPSHOT_NAME_PROVIDER);

    backupRepository.executeSnapshotting(
        new SnapshotRequest(
            REPOSITORY_NAME,
            SNAPSHOT_NAME_PROVIDER.getSnapshotName(metadata),
            new SnapshotIndexCollection(indices, List.of()),
            metadata),
        () -> {
          zeebeIndicesBackupStatus.set("COMPLETED");
          LOGGER.info("Backup of Zeebe secondary-storage records completed");
        },
        () -> {
          zeebeIndicesBackupStatus.set("FAILURE");
          LOGGER.error("Failed to take backup of Zeebe secondary-storage records");
        });

    Awaitility.await("Zeebe indices have been backed up")
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
                    .allSatisfy(
                        detail -> assertThat(detail.getState()).isEqualTo(StateCode.COMPLETED));
              } catch (final Exception e) {
                throw new AssertionError("Backup not found", e);
              }
            });
  }
}
