/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema.strategy;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_OPENSEARCH_VERSION;

import io.camunda.application.Profile;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneBackupManager;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterSchemaManager;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public final class OpenSearchBackendStrategy implements SearchBackendStrategy {

  private static final String ADMIN_USER = "camunda-admin";
  private static final String ADMIN_PASSWORD = "AdminPassword123!";
  private static final String APP_USER = "camunda-app";
  private static final String APP_PASSWORD = "AppPassword123!";
  private static final String INITIAL_ADMIN_PASSWORD = "Strong-Initial-Password123!";

  private GenericContainer<?> container;
  private OpenSearchClient adminClient;
  private String url;

  @Override
  public void startContainer() throws Exception {
    if (container != null) {
      throw new IllegalStateException("Container is already started");
    }
    container =
        new GenericContainer<>(
                DockerImageName.parse("opensearchproject/opensearch")
                    .withTag(SUPPORTED_OPENSEARCH_VERSION))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "false")
            .withEnv("plugins.security.ssl.http.enabled", "false")
            .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", INITIAL_ADMIN_PASSWORD)
            // Configure with allowed repository storage path
            .withEnv("path.repo", "~/")
            .withExposedPorts(9200)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withStartupAttempts(3)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(9200)
                    .withBasicCredentials("admin", INITIAL_ADMIN_PASSWORD)
                    .forStatusCodeMatching(response -> response == 200 || response == 401)
                    .withReadTimeout(Duration.ofSeconds(10))
                    .withStartupTimeout(Duration.ofMinutes(5)));

    container.start();
    createUsersAndRoles();
    url = "http://" + container.getHost() + ":" + container.getMappedPort(9200);
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
    adminClient = new OpensearchConnector(cfg).createClient();
  }

  @Override
  public void createSchema() throws Exception {
    final var adminCfg = exporterAdminConfig();
    final var schemaManager = new OpensearchExporterSchemaManager(adminCfg);
    schemaManager.createSchema(adminClient.info().version().number());
  }

  @Override
  public void configureStandaloneSchemaManager(final TestStandaloneSchemaManager schemaManager) {
    schemaManager
        .withProperty(
            "zeebe.broker.exporters.opensearch.class-name", OpensearchExporter.class.getName())
        .withProperty("zeebe.broker.exporters.opensearch.args.url", url)
        .withProperty("zeebe.broker.exporters.opensearch.args.authentication.username", ADMIN_USER)
        .withProperty(
            "zeebe.broker.exporters.opensearch.args.authentication.password", ADMIN_PASSWORD)
        .withSecondaryStorageType(SecondaryStorageType.opensearch)
        .withUnifiedConfig(
            cfg -> {
              final var opensearch = cfg.getData().getSecondaryStorage().getOpensearch();
              opensearch.setUrl(url);
              opensearch.setUsername(ADMIN_USER);
              opensearch.setPassword(ADMIN_PASSWORD);
            });
  }

  @Override
  public void configureStandaloneBackupManager(
      final TestStandaloneBackupManager backupManager, final String repositoryName) {
    backupManager
        .withSecondaryStorageType(SecondaryStorageType.opensearch)
        .withUnifiedConfig(
            cfg -> {
              final var opensearch = cfg.getData().getSecondaryStorage().getOpensearch();
              opensearch.setUrl(url);
              opensearch.setUsername(ADMIN_USER);
              opensearch.setPassword(ADMIN_PASSWORD);

              cfg.getData()
                  .getSecondaryStorage()
                  .getOpensearch()
                  .getBackup()
                  .setRepositoryName(repositoryName);
            });
  }

  @Override
  public void configureCamundaApplication(final TestCamundaApplication camunda) {
    camunda
        .withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
        .withUnauthenticatedAccess()
        .withProperty(CREATE_SCHEMA_PROPERTY, "false")
        .withProperty("camunda.operate.opensearch.health-check-enabled", "false")
        .withProperty("camunda.tasklist.opensearch.health-check-enabled", "false")
        .withSecondaryStorageType(SecondaryStorageType.opensearch)
        .withUnifiedConfig(
            cfg -> {
              cfg.getData().getSecondaryStorage().getOpensearch().setUrl(url);
              cfg.getData().getSecondaryStorage().getOpensearch().setUsername(APP_USER);
              cfg.getData().getSecondaryStorage().getOpensearch().setPassword(APP_PASSWORD);
              cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
            })
        .withExporter(
            CamundaExporter.class.getSimpleName(),
            cfg -> {
              cfg.setClassName(CamundaExporter.class.getName());
              cfg.setArgs(
                  Map.of(
                      "connect",
                      Map.of(
                          "url",
                          url,
                          "type",
                          "opensearch",
                          "username",
                          APP_USER,
                          "password",
                          APP_PASSWORD),
                      "history",
                      Map.of("waitPeriodBeforeArchiving", "1s"),
                      "createSchema",
                      false));
            })
        .withExporter(
            OpensearchExporter.class.getSimpleName(),
            cfg -> {
              cfg.setClassName(OpensearchExporter.class.getName());
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
    return adminClient.indices().getIndexTemplate(r -> r.name(namePattern)).indexTemplates().size();
  }

  @Override
  public long searchByKey(final String indexPattern, final long key) throws Exception {
    final var resp =
        adminClient.search(
            s ->
                s.index(indexPattern)
                    .query(
                        q -> q.term(t -> t.field("key").value(FieldValue.of(String.valueOf(key))))),
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
            q -> q.name(repositoryName).type("fs").settings(s -> s.location(repositoryName)));
  }

  /**
   * Creates the OpenSearch security users and roles required for the acceptance tests.
   *
   * <p>Security model:
   *
   * <ul>
   *   <li>{@code camunda_app_role}: application role used by the Camunda components under test
   *       (e.g. exporters, web applications) to access their indices. It is restricted to:
   *       <ul>
   *         <li>Cluster-level permission {@code indices:data/read/scroll/clear} to clean up scroll
   *             contexts created during queries.
   *         <li>Index-level permissions on the {@code zeebe-*}, {@code operate-*}, {@code
   *             tasklist-*}, and {@code camunda-*} indices, allowing: {@code indices:data/write/*},
   *             {@code indices:data/read/*}, {@code indices:admin/create}, and {@code
   *             indices:admin/shards/search_shards}.
   *       </ul>
   *       These permissions are intended to be the minimal set required for the application to read
   *       from and write to its own indices in this test environment.
   *   <li>{@code camunda-app} user ({@link #APP_USER}): test application user that authenticates
   *       against OpenSearch using {@link #APP_PASSWORD} and is assigned only the {@code
   *       camunda_app_role}. This user is used by the Camunda application in tests and should not
   *       have cluster-wide administrative rights.
   *   <li>{@code camunda-admin} user ({@link #ADMIN_USER}): administrative test user that
   *       authenticates using {@link #ADMIN_PASSWORD} and is assigned the built-in {@code
   *       all_access} role.
   * </ul>
   *
   * <p>The {@code all_access} role is provided by the OpenSearch security plugin as a
   * superuser-style role. In the test security configuration used here, the Camunda admin role
   * ({@code camunda_admin_role}) is mapped to {@code all_access}, meaning that granting {@code
   * all_access} to the {@code camunda-admin} user also grants it the effective privileges of {@code
   * camunda_admin_role}. This is acceptable in this QA context to simplify setup and ensure the
   * admin user can perform any required operation on the test cluster, but it should not be used as
   * a reference for production deployments.
   *
   * @throws IOException if the role or user creation HTTP calls fail
   * @throws InterruptedException if the container execution is interrupted
   */
  private void createUsersAndRoles() throws IOException, InterruptedException {
    // Create camunda_app_role
    container.execInContainer(
        "curl",
        "-X",
        "PUT",
        "-u",
        "admin:" + INITIAL_ADMIN_PASSWORD,
        "-H",
        "Content-Type: application/json",
        "http://localhost:9200/_plugins/_security/api/roles/camunda_app_role",
        "-d",
        """
        {
          "cluster_permissions": [
            "indices:data/read/scroll/clear"
          ],
          "index_permissions": [
            {
              "index_patterns": [
                "zeebe-*",
                "operate-*",
                "tasklist-*",
                "camunda-*"
              ],
              "allowed_actions": [
                "indices:data/write/*",
                "indices:data/read/*",
                "indices:admin/create",
                "indices:admin/shards/search_shards"
              ]
            }
          ]
        }""");

    // Create camunda-app user
    container.execInContainer(
        "curl",
        "-X",
        "PUT",
        "-u",
        "admin:" + INITIAL_ADMIN_PASSWORD,
        "-H",
        "Content-Type: application/json",
        "http://localhost:9200/_plugins/_security/api/internalusers/" + APP_USER,
        "-d",
        """
        {
          "password": "%s",
          "opendistro_security_roles": [
            "camunda_app_role"
          ]
        }"""
            .formatted(APP_PASSWORD));

    // Create camunda-admin user
    container.execInContainer(
        "curl",
        "-X",
        "PUT",
        "-u",
        "admin:" + INITIAL_ADMIN_PASSWORD,
        "-H",
        "Content-Type: application/json",
        "http://localhost:9200/_plugins/_security/api/internalusers/" + ADMIN_USER,
        "-d",
        """
        {
          "password": "%s",
          "opendistro_security_roles": [
            "all_access"
          ]
        }"""
            .formatted(ADMIN_PASSWORD));
  }

  private OpensearchExporterConfiguration exporterAdminConfig() {
    final var cfg = new OpensearchExporterConfiguration();
    cfg.url = url;
    cfg.getAuthentication().setUsername(ADMIN_USER);
    cfg.getAuthentication().setPassword(ADMIN_PASSWORD);
    cfg.index.createTemplate = true;
    return cfg;
  }
}
