/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema.strategy;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.snapshot.SnapshotInfo;
import io.camunda.application.Profile;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneBackupManager;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.ElasticsearchExporterSchemaManager;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public final class ElasticsearchBackendStrategy implements SearchBackendStrategy {

  public static final String APP_ROLE = "camunda_app_role";
  public static final String APP_ROLE_DEFINITION =
      // language=yaml
      """
          camunda_app_role:
            indices:
              - names: ['zeebe-*', 'operate-*', 'tasklist-*', 'camunda-*']
                privileges: ['manage', 'read', 'write']
          """;
  private static final String ADMIN_USER = "camunda-admin";
  private static final String ADMIN_PASSWORD = "admin123";
  private static final String ADMIN_ROLE = "superuser";
  private static final String APP_USER = "camunda-app";
  private static final String APP_PASSWORD = "app123";
  private String url;
  private ElasticsearchContainer container;
  private ElasticsearchClient adminClient;

  @Override
  public void startContainer() throws Exception {
    if (container != null) {
      throw new IllegalStateException("Container is already started");
    }
    container =
        new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(SUPPORTED_ELASTICSEARCH_VERSION))
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("xpack.security.enabled", "true")
            // Configure with allowed repository storage path
            .withEnv("path.repo", "~/")
            // to be able to delete indices with wildcards
            .withEnv("action.destructive_requires_name", "false")
            .withStartupAttempts(3)
            .withCopyToContainer(
                Transferable.of(APP_ROLE_DEFINITION), "/usr/share/elasticsearch/config/roles.yml");
    container.start();
    container.execInContainer("elasticsearch-users", "useradd", ADMIN_USER, "-p", ADMIN_PASSWORD);
    container.execInContainer("elasticsearch-users", "roles", ADMIN_USER, "-a", ADMIN_ROLE);
    container.execInContainer("elasticsearch-users", "useradd", APP_USER, "-p", APP_PASSWORD);
    container.execInContainer("elasticsearch-users", "roles", APP_USER, "-a", APP_ROLE);
    url = "http://" + container.getHttpHostAddress();
  }

  @Override
  public GenericContainer<?> getContainer() {
    return container;
  }

  @Override
  public void createAdminClient() throws Exception {

    final var cfg = new ConnectConfiguration();
    cfg.setUrl(url);
    cfg.setUsername(ADMIN_USER);
    cfg.setPassword(ADMIN_PASSWORD);
    adminClient = new ElasticsearchConnector(cfg).createClient();

    // ES can be slow to assign roles and permissions, so we wait until we can successfully connect
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .until(() -> adminClient.info() != null);
  }

  @Override
  public void createSchema() throws Exception {
    final var adminCfg = exporterAdminConfig();
    final var schemaManager = new ElasticsearchExporterSchemaManager(adminCfg);
    schemaManager.createSchema(adminClient.info().version().number());
  }

  @Override
  public void configureStandaloneSchemaManager(final TestStandaloneSchemaManager schemaManager) {
    schemaManager
        .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
        .withUnifiedConfig(
            cfg -> {
              final var elasticsearch = cfg.getData().getSecondaryStorage().getElasticsearch();
              elasticsearch.setUrl(url);
              elasticsearch.setUsername(ADMIN_USER);
              elasticsearch.setPassword(ADMIN_PASSWORD);
            })
        .withProperty(
            "zeebe.broker.exporters.elasticsearch.class-name",
            ElasticsearchExporter.class.getName())
        .withProperty("zeebe.broker.exporters.elasticsearch.args.url", url)
        .withProperty(
            "zeebe.broker.exporters.elasticsearch.args.authentication.username", ADMIN_USER)
        .withProperty(
            "zeebe.broker.exporters.elasticsearch.args.authentication.password", ADMIN_PASSWORD);
  }

  @Override
  public void configureStandaloneBackupManager(
      final TestStandaloneBackupManager backupManager, final String repositoryName) {
    backupManager
        .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
        .withUnifiedConfig(
            cfg -> {
              final var elasticsearch = cfg.getData().getSecondaryStorage().getElasticsearch();
              elasticsearch.setUrl(url);
              elasticsearch.setUsername(ADMIN_USER);
              elasticsearch.setPassword(ADMIN_PASSWORD);

              cfg.getData()
                  .getSecondaryStorage()
                  .getElasticsearch()
                  .getBackup()
                  .setRepositoryName(repositoryName);
            });
  }

  @Override
  public void configureCamundaApplication(final TestCamundaApplication camunda) {
    camunda
        .withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
        .withProperty(CREATE_SCHEMA_PROPERTY, "false")
        .withProperty("camunda.operate.elasticsearch.health-check-enabled", "false")
        .withProperty("camunda.tasklist.elasticsearch.health-check-enabled", "false")
        .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
        .withUnifiedConfig(
            cfg -> {
              cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(url);
              cfg.getData().getSecondaryStorage().getElasticsearch().setUsername(APP_USER);
              cfg.getData().getSecondaryStorage().getElasticsearch().setPassword(APP_PASSWORD);
              cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
            })
        .withExporter(
            CamundaExporter.class.getSimpleName(),
            cfg -> {
              cfg.setClassName(CamundaExporter.class.getName());
              cfg.setArgs(
                  Map.of(
                      "connect",
                      Map.of("username", APP_USER, "password", APP_PASSWORD, "url", url),
                      "history",
                      Map.of("waitPeriodBeforeArchiving", "1s"),
                      "createSchema",
                      false));
            })
        .withExporter(
            ElasticsearchExporter.class.getSimpleName(),
            cfg -> {
              cfg.setClassName(ElasticsearchExporter.class.getName());
              cfg.setArgs(
                  Map.of(
                      "url",
                      url,
                      "index",
                      Map.of("createTemplate", false),
                      "authentication",
                      Map.of("username", APP_USER, "password", APP_PASSWORD)));
            });
  }

  @Override
  public long countDocuments(final String indexPattern) throws Exception {
    return adminClient.count(c -> c.index(indexPattern)).count();
  }

  @Override
  public long countTemplates(final String namePattern) throws Exception {
    return adminClient.indices().getIndexTemplate(c -> c.name(namePattern)).indexTemplates().size();
  }

  @Override
  public long searchByKey(final String indexPattern, final long key) throws Exception {
    final var resp =
        adminClient.search(
            s -> s.index(indexPattern).query(q -> q.term(t -> t.field("key").value(key))),
            Object.class);
    return resp.hits().total() == null ? 0 : resp.hits().total().value();
  }

  @Override
  public void deleteIndices(final String indexName, final String... indexNames) throws IOException {
    adminClient.indices().delete(r -> r.index(indexName, indexNames).ignoreUnavailable(true));
  }

  @Override
  public boolean indicesExist(final String indexName, final String... indexNames) throws Exception {
    return adminClient
        .indices()
        .exists(r -> r.index(indexName, indexNames).allowNoIndices(false))
        .value();
  }

  @Override
  public List<String> getSuccessSnapshots(
      final String repositoryName, final String snapshotNamePrefix) throws Exception {
    return adminClient
        .snapshot()
        .get(r -> r.repository(repositoryName).snapshot(snapshotNamePrefix + "*"))
        .snapshots()
        .stream()
        .filter(info -> Objects.equals(info.state(), "SUCCESS"))
        .map(SnapshotInfo::snapshot)
        .toList();
  }

  /**
   * As documented in
   *
   * <p>https://docs.camunda.io/docs/self-managed/operational-guides/backup-restore/backup-and-restore/#restore
   */
  @Override
  public void restoreBackup(final String repositoryName, final String snapshot) throws IOException {
    adminClient
        .snapshot()
        .restore(r -> r.repository(repositoryName).snapshot(snapshot).waitForCompletion(true));
  }

  @Override
  public void createSnapshotRepository(final String repositoryName) throws IOException {
    adminClient
        .snapshot()
        .createRepository(
            q ->
                q.name(repositoryName)
                    .repository(r -> r.fs(fs -> fs.settings(s -> s.location(repositoryName)))));
  }

  private ElasticsearchExporterConfiguration exporterAdminConfig() {
    final var cfg = new ElasticsearchExporterConfiguration();
    cfg.url = url;
    cfg.getAuthentication().setUsername(ADMIN_USER);
    cfg.getAuthentication().setPassword(ADMIN_PASSWORD);
    cfg.index.createTemplate = true;
    return cfg;
  }
}
