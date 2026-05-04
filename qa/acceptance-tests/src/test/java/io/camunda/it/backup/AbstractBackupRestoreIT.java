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
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractBackupRestoreIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBackupRestoreIT.class);
  private static final SnapshotNameProvider SNAPSHOT_NAME_PROVIDER =
      new ZeebeSnapshotNameProvider();

  protected final String containerName =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase(Locale.ROOT);

  protected TestStandaloneApplication<?> application;
  protected CamundaClient camundaClient;
  protected ExportingActuator exportingActuator;
  protected BackupActuator backupActuator;
  protected DocumentClient webappsDBClient;
  protected GenericContainer<?> searchContainer;
  protected HistoryBackupClient historyBackupClient;
  protected MultiDbConfigurator configurator;
  protected WorkingDirectory workingDirectory;

  protected static Stream<DatabaseType> supportedDatabases() {
    final String configuredDatabase = System.getProperty("camunda.it.backup.databaseType");
    if (configuredDatabase != null && !configuredDatabase.isBlank()) {
      return Stream.of(DatabaseType.valueOf(configuredDatabase.toUpperCase(Locale.ROOT)));
    }
    return Stream.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH);
  }

  protected final TestStandaloneApplication<?> setup(
      final DatabaseType databaseType,
      final String repositoryName,
      final String indexPrefix,
      final Duration clientTimeout,
      final AzuriteContainer azuriteContainer,
      final Executor executor)
      throws Exception {
    application =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();
    configurator = new MultiDbConfigurator(application);
    application.withUnifiedConfig(
        cfg -> configureZeebeBackupStore(cfg, databaseType, repositoryName, azuriteContainer));

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
            configurator.configureElasticsearchSupportIncludingOldExporter(dbUrl, indexPrefix);
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
                dbUrl, indexPrefix, "admin", "admin");
            yield container;
          }
          default ->
              throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        };

    application.start().awaitCompleteTopology();

    refreshCamundaClient(clientTimeout, client -> {});
    exportingActuator = ExportingActuator.of(application);
    backupActuator = BackupActuator.of(application);
    historyBackupClient = HistoryBackupClient.of(application);
    webappsDBClient = DocumentClient.create(dbUrl, databaseType, executor);
    webappsDBClient.createRepository(repositoryName);
    workingDirectory = application.bean(WorkingDirectory.class);
    return application;
  }

  protected final void refreshCamundaClient(
      final Duration clientTimeout, final Consumer<CamundaClient> clientConsumer) {
    CloseHelper.quietClose(camundaClient);
    camundaClient = application.newClientBuilder().defaultRequestTimeout(clientTimeout).build();
    clientConsumer.accept(camundaClient);
  }

  protected final void tearDown(final AutoCloseable... closeables) {
    CloseHelper.quietCloseAll(closeables);
  }

  protected final void configureZeebeBackupStore(
      final Camunda cfg,
      final DatabaseType databaseType,
      final String repositoryName,
      final AzuriteContainer azuriteContainer) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    final var azure = backup.getAzure();

    backup.setStore(BackupStoreType.AZURE);
    azure.setBasePath(containerName);
    azure.setConnectionString(azuriteContainer.externalConnectionString());

    if (databaseType.isElasticSearch()) {
      cfg.getData()
          .getSecondaryStorage()
          .getElasticsearch()
          .getBackup()
          .setRepositoryName(repositoryName);
    } else {
      cfg.getData()
          .getSecondaryStorage()
          .getOpensearch()
          .getBackup()
          .setRepositoryName(repositoryName);
    }
  }

  protected final void restoreZeebe(final long backupId, final AzuriteContainer azuriteContainer) {
    final var unifiedRestoreConfig = new Camunda();
    final var backup = unifiedRestoreConfig.getData().getPrimaryStorage().getBackup();

    backup.setStore(BackupStoreType.AZURE);
    backup.getAzure().setBasePath(containerName);
    backup.getAzure().setConnectionString(azuriteContainer.externalConnectionString());

    final var brokerCfg = application.unifiedConfig();
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

    try (final var restoreApp = new TestRestoreApp(unifiedRestoreConfig).withBackupId(backupId)) {
      assertThatNoException().isThrownBy(restoreApp::start);
    }
  }

  protected final List<String> takeHistoryBackup(
      final long backupId, final String completionDescription) {
    final var takeResponse = historyBackupClient.takeBackup(backupId);
    assertThat(takeResponse)
        .extracting(TakeBackupHistoryResponse::getScheduledSnapshots)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final List<String> snapshots = takeResponse.getScheduledSnapshots();

    Awaitility.await(completionDescription)
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              try {
                final var backupResponse = historyBackupClient.getBackup(backupId);
                assertThat(backupResponse.getState()).isEqualTo(BackupStateDto.COMPLETED);
                assertThat(backupResponse.getDetails())
                    .allMatch(detail -> detail.getState().equals("SUCCESS"));
              } catch (final NotFound e) {
                throw new AssertionError("Backup not found", e);
              }
            });

    for (final boolean verbose : List.of(true, false)) {
      assertThat(historyBackupClient.getBackups(verbose, backupId + "*"))
          .allSatisfy(state -> assertThat(state.getState()).isEqualTo(BackupStateDto.COMPLETED));
    }

    return snapshots;
  }

  protected final void takeZeebeBackup(final long backupId, final String repositoryName) {
    final var metadata = new Metadata(backupId, "current", 1, 1);
    final List<String> indices;
    try {
      indices = webappsDBClient.cat(configurator.zeebeIndexPrefix());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    assertThat(indices).isNotEmpty();

    final var zeebeIndicesBackupStatus = new AtomicReference<String>();
    final var backupRepository =
        webappsDBClient.zeebeBackupRepository(repositoryName, SNAPSHOT_NAME_PROVIDER);

    backupRepository.executeSnapshotting(
        new SnapshotRequest(
            repositoryName,
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

    backupActuator.take(backupId);
    Awaitility.await("Zeebe backup completed")
        .untilAsserted(
            () -> {
              try {
                final var status = backupActuator.status(backupId);
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
