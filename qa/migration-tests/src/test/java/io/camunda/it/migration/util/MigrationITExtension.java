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
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.PROP_CAMUNDA_IT_DATABASE_TYPE;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATABASE_READINESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
  private static final String TASKLIST = "tasklist";
  private static final String OPERATE = "operate";
  private final List<AutoCloseable> closables = new ArrayList<>();
  private Map<String, String> initialEnvOverrides = new HashMap<>();
  private Map<String, String> upgradeSystemPropertyOverrides = new HashMap<>();
  private final DatabaseType databaseType;
  private String indexPrefix;
  private String databaseUrl;
  private CamundaMigrator migrator;
  private Path tempDir;
  private MigrationDatabaseChecks migrationDatabaseChecks;
  private BiConsumer<DatabaseType, CamundaMigrator> beforeUpgradeConsumer = null;
  private Consumer<Map<String, Object>> exporterArgsOverride = null;
  private Profile[] postUpdateProfiles;
  private boolean authenticationEnabled = true;

  public MigrationITExtension() {
    final String property = System.getProperty(PROP_CAMUNDA_IT_DATABASE_TYPE);
    databaseType =
        property == null ? DatabaseType.LOCAL : DatabaseType.valueOf(property.toUpperCase());
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
      tempDir = Files.createTempDirectory(indexPrefix + "-zeebe");
      setupDatabase();
      migrator = new CamundaMigrator(indexPrefix, tempDir, databaseType, databaseUrl);
      migrator.initialize(initialEnvOverrides);
      if (beforeUpgradeConsumer != null) {
        beforeUpgradeConsumer.accept(databaseType, migrator);
      }
      upgrade(upgradeSystemPropertyOverrides);
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

  /**
   * Allows to provide an override for the CamundaExporter arguments that will be used during the
   * upgrade process. Only applies to the 8.8 started application.
   */
  public MigrationITExtension withCamundaExporterArgsOverride(
      final Consumer<Map<String, Object>> override) {
    exporterArgsOverride = override;
    return this;
  }

  /**
   * Allows the provision of system property overrides that will be used by the migration container.
   * This can be used to enable features that are required for the upgrade process. For example
   * "camunda.database.retention.enabled" -> "true".
   *
   * @param systemPropertyOverrides the system property overrides to set
   * @return self for chaining
   */
  public MigrationITExtension withUpgradeSystemPropertyOverrides(
      final Map<String, String> systemPropertyOverrides) {
    upgradeSystemPropertyOverrides = systemPropertyOverrides;
    return this;
  }

  /// Include additional profiles that should be activated after the update
  public MigrationITExtension withPostUpdateAdditionalProfiles(final Profile... profiles) {
    postUpdateProfiles = profiles;
    return this;
  }

  public MigrationITExtension withAuthenticationDisabled() {
    authenticationEnabled = false;
    return this;
  }

  public String getDatabaseUrl() {
    return databaseUrl;
  }

  public boolean isElasticSearch() {
    return databaseType == DatabaseType.ES || databaseType == DatabaseType.LOCAL;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  private void upgrade(final Map<String, String> envOverrides) {

    // Ensure that the importers have flushed their positions before starting the upgrade
    // if there are initialization data and importers are not disabled
    if (beforeUpgradeConsumer != null && !areImportersDisabled()) {
      awaitImportersFlushed();
    }
    migrator.update(envOverrides, exporterArgsOverride, authenticationEnabled, postUpdateProfiles);
    awaitExporterReadiness();
    awaitImportersFinished();

    if (shouldWaitForMigrations()) {
      awaitMigrationsFinished();
    }
  }

  private boolean shouldWaitForMigrations() {
    return postUpdateProfiles != null
        && postUpdateProfiles.length > 0
        && (List.of(postUpdateProfiles).contains(Profile.PROCESS_MIGRATION)
            || List.of(postUpdateProfiles).contains(Profile.USAGE_METRIC_MIGRATION)
            || List.of(postUpdateProfiles).contains(Profile.TASK_MIGRATION));
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
        suppressEsWarnings();
        increaseIlmPolling();
        expectedDescriptors = new IndexDescriptors(indexPrefix, true).all();
      }
      case ES -> {
        databaseUrl = DEFAULT_ES_URL;
        suppressEsWarnings();
        increaseIlmPolling();
        expectedDescriptors = new IndexDescriptors(indexPrefix, true).all();
      }
      case OS -> {
        expectedDescriptors = new IndexDescriptors(indexPrefix, false).all();
        databaseUrl = DEFAULT_OS_URL;
        increaseIsmPolling();
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

  // In these scenarios we increase the intervals of the Importers so more warning logs are reported
  // by ES. To keep the test output clean we suppress these warnings.
  private void suppressEsWarnings() {
    try {
      HttpClient.newHttpClient()
          .send(
              HttpRequest.newBuilder(URI.create(databaseUrl + "/_cluster/settings"))
                  .PUT(
                      BodyPublishers.ofString(
                          "{\"persistent\": {\"logger.org.elasticsearch.deprecation\": \"OFF\"}}"))
                  .header("Content-Type", "application/json")
                  .build(),
              BodyHandlers.ofString());
    } catch (final IOException | InterruptedException ignore) {
    }
  }

  private void increaseIlmPolling() {
    try {
      HttpClient.newHttpClient()
          .send(
              HttpRequest.newBuilder(URI.create(databaseUrl + "/_cluster/settings"))
                  .PUT(
                      BodyPublishers.ofString(
                          "{\"persistent\": {\"indices.lifecycle.poll_interval\": \"10s\"}}"))
                  .header("Content-Type", "application/json")
                  .build(),
              BodyHandlers.ofString());
    } catch (final IOException | InterruptedException ignore) {
    }
  }

  private void increaseIsmPolling() {
    try {
      HttpClient.newHttpClient()
          .send(
              HttpRequest.newBuilder(URI.create(databaseUrl + "/_cluster/settings"))
                  .PUT(
                      BodyPublishers.ofString(
                          "{\"persistent\": {\"plugins.index_state_management.job_interval\": \"1\"	}}"))
                  .header("Content-Type", "application/json")
                  .build(),
              BodyHandlers.ofString());
    } catch (final IOException | InterruptedException ignore) {
    }
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

  private void awaitMigrationsFinished() {
    Awaitility.await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(migrator.isMigrationCompleted()).isTrue());
  }

  private boolean areImportersDisabled() {
    return initialEnvOverrides
            .getOrDefault("CAMUNDA_TASKLIST_IMPORTERENABLED", "true")
            .equals("false")
        && initialEnvOverrides
            .getOrDefault("CAMUNDA_OPERATE_IMPORTERENABLED", "true")
            .equals("false");
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
