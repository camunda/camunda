/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.DEFAULT_ES_URL;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.DEFAULT_OS_URL;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATABASE_READINESS;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.ModifierSupport;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class MigrationITExtension
    implements AfterAllCallback, BeforeAllCallback, ParameterResolver {
  public static final String PROP_CAMUNDA_IT_DATABASE_TYPE =
      "test.integration.camunda.database.type";
  private static final String TASKLIST = "tasklist";
  private static final String OPERATE = "operate";

  private final List<AutoCloseable> closables = new ArrayList<>();
  private Map<String, String> initialEnvOverrides = new HashMap<>();
  private Map<String, String> upgradeEnvOverrides = new HashMap<>();
  private final DatabaseType databaseType;
  private String indexPrefix;
  private String databaseUrl;
  private CamundaMigrator migrator;
  private final Path tempDir;
  private MigrationDatabaseChecks migrationDatabaseChecks;
  private BiConsumer<DatabaseType, CamundaMigrator> beforeUpgradeConsumer = (db, migrator) -> {};

  public MigrationITExtension() {
    final String property = System.getProperty(PROP_CAMUNDA_IT_DATABASE_TYPE);
    databaseType = property == null ? DatabaseType.LOCAL : DatabaseType.valueOf(property);
    try {
      tempDir = Files.createTempDirectory(indexPrefix + "-zeebe");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void afterAll(final ExtensionContext context) throws IOException {
    final var clazz = context.getTestClass().get();
    if (!isNestedClass(clazz)) {
      CloseHelper.quietClose(migrator);
      CloseHelper.quietCloseAll(closables);
      FileUtil.deleteFolderIfExists(tempDir);
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final var clazz = context.getTestClass().get();
    if (!isNestedClass(clazz)) {
      indexPrefix = clazz.getSimpleName().toLowerCase();
      setupDatabase();
      migrator = new CamundaMigrator(indexPrefix, tempDir, databaseType, databaseUrl);
      migrator.initialize(initialEnvOverrides);
      beforeUpgradeConsumer.accept(databaseType, migrator);
      upgrade(upgradeEnvOverrides);
    }
  }

  /**
   * Allows to provide a consumer that will be called before the upgrade process starts. This can be
   * used to setup the database with data that is required for the upgrade process.
   *
   * @param upgradeConsumer
   * @return self for chaining
   */
  public MigrationITExtension withBeforeUpgradeConsumer(
      final BiConsumer<DatabaseType, CamundaMigrator> upgradeConsumer) {
    beforeUpgradeConsumer = upgradeConsumer;
    return this;
  }

  public MigrationITExtension withInitialEnvOverrides(final Map<String, String> envOverrides) {
    initialEnvOverrides = envOverrides;
    return this;
  }

  public MigrationITExtension withUpgradeEnvOverrides(final Map<String, String> envOverrides) {
    upgradeEnvOverrides = envOverrides;
    return this;
  }

  private void upgrade(final Map<String, String> envOverrides) {

    awaitImportersFlushed();

    migrator.update(envOverrides);
    awaitExporterReadiness();

    /* Ingest an 8.8 Record in order to trigger importers empty batch counting */
    ingestRecordToTriggerImporters(migrator.getCamundaClient());

    awaitImportersFinished();
  }

  private boolean isNestedClass(final Class<?> currentClass) {
    return !ModifierSupport.isStatic(currentClass) && currentClass.isMemberClass();
  }

  /** TODO: Merge this with the {@link CamundaMultiDBExtension} setup */
  private void setupDatabase() {
    final Collection<IndexDescriptor> expectedDescriptors;
    switch (databaseType) {
      case LOCAL -> {
        final ElasticsearchContainer elasticsearchContainer =
            TestSearchContainers.createDefeaultElasticsearchContainer();
        elasticsearchContainer.setPortBindings(List.of("9200:9200"));
        elasticsearchContainer.start();
        closables.add(elasticsearchContainer);
        databaseUrl = "http://" + elasticsearchContainer.getHttpHostAddress();
        expectedDescriptors = new IndexDescriptors(indexPrefix, true).all();
      }
      case ES -> {
        expectedDescriptors = new IndexDescriptors(indexPrefix, true).all();
        databaseUrl = DEFAULT_ES_URL;
      }
      case OS -> {
        expectedDescriptors = new IndexDescriptors(indexPrefix, false).all();
        databaseUrl = DEFAULT_OS_URL;
      }
      default -> throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }

    migrationDatabaseChecks =
        new MigrationDatabaseChecks(databaseUrl, expectedDescriptors, indexPrefix);
    closables.add(migrationDatabaseChecks);

    Awaitility.await("Await secondary storage connection")
        .timeout(TIMEOUT_DATABASE_READINESS)
        .until(migrationDatabaseChecks::validateConnection);
  }

  private void awaitExporterReadiness() {
    Awaitility.await("Await database and exporter readiness")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(migrationDatabaseChecks::validateSchema);
  }

  private void awaitImportersFinished() {
    Awaitility.await("Await Importers finished")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () ->
                migrationDatabaseChecks.checkImportersFinished(OPERATE)
                    && migrationDatabaseChecks.checkImportersFinished(TASKLIST));
  }

  private void awaitImportersFlushed() {
    Awaitility.await("Await Import Positions have been flushed")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .until(
            () ->
                migrationDatabaseChecks.checkImportPositionsFlushed(OPERATE)
                    && migrationDatabaseChecks.checkImportPositionsFlushed(TASKLIST));
  }

  private void ingestRecordToTriggerImporters(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/error-end-event.bpmn")
        .send()
        .join();
  }

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == CamundaClient.class
        || parameterContext.getParameter().getType() == DocumentBasedSearchClient.class
        || parameterContext.getParameter().getType() == DatabaseType.class
        || parameterContext.getParameter().getType() == CamundaMigrator.class;
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (parameterContext.getParameter().getType() == DatabaseType.class) {
      return databaseType;
    } else if (parameterContext.getParameter().getType() == CamundaClient.class) {
      return migrator.getCamundaClient();
    } else if (parameterContext.getParameter().getType() == DocumentBasedSearchClient.class) {
      return migrator.getSearchClient();
    } else if (parameterContext.getParameter().getType() == CamundaMigrator.class) {
      return migrator;
    }
    return null;
  }
}
