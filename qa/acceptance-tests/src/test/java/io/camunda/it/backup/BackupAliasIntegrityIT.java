/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class BackupAliasIntegrityIT extends AbstractBackupRestoreIT {

  private static final String REPOSITORY_NAME = "test-repository";
  private static final String INDEX_PREFIX = "";
  private static final String TEST_INDEX_1 = "operate-post-importer-queue-8.3.0_";
  private static final String TEST_INDEX_2 = "operate-incident-8.3.1_";
  private static final long BACKUP_ID = 1L;
  private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(60);

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
    VersionUtil.overridePrerelease();
  }

  @AfterEach
  public void afterEach() {
    CloseHelper.quietCloseAll(
        webappsDBClient, camundaClient, searchContainer, testStandaloneApplication);
  }

  @AfterAll
  public static void afterAll() {
    VersionUtil.resetVersionForTesting();
  }

  @ParameterizedTest
  @MethodSource("supportedDatabases")
  void shouldFailBackupWhenAliasPointsToMultipleIndexes(final DatabaseType databaseType)
      throws Exception {
    testStandaloneApplication =
        super.setup(
            databaseType,
            REPOSITORY_NAME,
            INDEX_PREFIX,
            CLIENT_TIMEOUT,
            AZURITE_CONTAINER,
            EXECUTOR);

    testStandaloneApplication.awaitCompleteTopology();

    // given: pick two existing indices and assign the same alias to both
    final var indices = webappsDBClient.cat(INDEX_PREFIX);
    assertThat(indices).hasSizeGreaterThanOrEqualTo(2);
    assertThat(indices).contains(TEST_INDEX_1);
    assertThat(indices).contains(TEST_INDEX_2);

    final String duplicateAlias = "duplicate-alias-test";
    webappsDBClient.createAlias(TEST_INDEX_1, duplicateAlias);
    webappsDBClient.createAlias(TEST_INDEX_2, duplicateAlias);

    // when / then
    assertThatThrownBy(() -> historyBackupClient.takeBackup(BACKUP_ID))
        .hasMessageContaining("Errors when validating alias integrity")
        .hasMessageContaining(TEST_INDEX_1)
        .hasMessageContaining(TEST_INDEX_2);
  }

  @ParameterizedTest
  @MethodSource("supportedDatabases")
  void shouldFailBackupWhenIndexIsMissingExpectedAlias(final DatabaseType databaseType)
      throws Exception {
    testStandaloneApplication =
        super.setup(
            databaseType,
            REPOSITORY_NAME,
            INDEX_PREFIX,
            CLIENT_TIMEOUT,
            AZURITE_CONTAINER,
            EXECUTOR);

    testStandaloneApplication.awaitCompleteTopology();

    // pre-check: the test index exists
    final var indices = webappsDBClient.cat(INDEX_PREFIX);
    assertThat(indices).contains(TEST_INDEX_1);

    // alter the schema by removing an alias
    final String aliasToRemove = TEST_INDEX_1 + "alias";
    webappsDBClient.deleteAlias(TEST_INDEX_1, aliasToRemove);

    // when / then
    assertThatThrownBy(() -> historyBackupClient.takeBackup(BACKUP_ID))
        .hasMessageContaining("Errors when validating alias integrity")
        .hasMessageContaining(TEST_INDEX_1);
  }
}
