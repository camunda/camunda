/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets;
import io.camunda.configuration.Secrets.FileInternalConfig;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Rotates the secondary-storage credentials and the URL of one physical tenant in its file secret
 * store while the node runs, against a live PostgreSQL, and checks that the tenant's readers and
 * its exporter keep working on the new values with no restart, and that the other tenants are not
 * touched.
 *
 * <p>Every tenant reads its connection settings as {@code camunda.secrets.<name>} references from
 * its own internal-config directory, refreshed every second.
 *
 * <ul>
 *   <li>{@code tenanta} rotates its credentials the way a dual-user rotation does on PostgreSQL,
 *       which has one password per role: the new login role is a member of the old one, so both are
 *       valid during the overlap, and the old one is revoked once nothing uses it any more.
 *   <li>{@code tenantb} is handed a wrong password.
 *   <li>{@code tenantc} moves to another URL for the same data.
 *   <li>{@code default} is never touched.
 * </ul>
 */
@Tag("rdbms")
@Timeout(300)
@ZeebeIntegration
final class PhysicalTenantRdbmsSecretRotationIT {

  private static final String DEFAULT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String ROTATED = "tenanta";
  private static final String WRONG = "tenantb";
  private static final String MOVED = "tenantc";
  private static final List<String> TENANTS_IDS = List.of(DEFAULT, ROTATED, WRONG, MOVED);

  private static final String DATABASE = "camunda";
  private static final String ADMIN = "camunda";
  private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(1);
  private static final Duration AWAIT = Duration.ofSeconds(60);

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName(DATABASE)
          .withUsername(ADMIN)
          .withPassword(ADMIN)
          .withStartupTimeout(Duration.ofMinutes(5));

  private static final Path SECRETS;
  private static final PhysicalTenantsITHelper TENANTS;

  static {
    POSTGRES.start();
    try {
      SECRETS = Files.createTempDirectory("secret-rotation-");
      TENANTS_IDS.forEach(PhysicalTenantRdbmsSecretRotationIT::provision);
    } catch (final IOException | RuntimeException e) {
      POSTGRES.stop();
      throw new IllegalStateException(e);
    }
    final var builder = PhysicalTenantsITHelper.builder();
    TENANTS_IDS.forEach(
        tenant ->
            builder.withTenant(
                tenant,
                Storage.rdbms(
                    "camunda.secrets.db-url",
                    "camunda.secrets.db-username",
                    "camunda.secrets.db-password")));
    TENANTS = builder.build();
  }

  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER = configure();

  @AfterAll
  static void tearDown() throws IOException {
    POSTGRES.stop();
    try (final var files = Files.walk(SECRETS)) {
      files.sorted((a, b) -> b.compareTo(a)).forEach(PhysicalTenantRdbmsSecretRotationIT::delete);
    }
  }

  @Test
  void shouldMoveReadersAndExporterToRotatedCredentialsWithoutARestart() throws Exception {
    // given
    final var defaultPool = pool(DEFAULT);
    final var processId = Strings.newRandomValidBpmnId();
    try (final var client = client(ROTATED)) {
      awaitExported(client, deployAndStart(client, processId));
      executeAsAdmin("CREATE ROLE pt_tenanta_v2 LOGIN PASSWORD 'v2' IN ROLE pt_tenanta");

      // when
      writeSecret(ROTATED, "db-username", "pt_tenanta_v2");
      writeSecret(ROTATED, "db-password", "v2");
      Awaitility.await("tenanta's pool connects only as the new role")
          .atMost(AWAIT)
          .untilAsserted(
              () -> {
                assertThat(sessionCount("pt_tenanta_v2")).isPositive();
                assertThat(sessionCount("pt_tenanta")).isZero();
              });
      executeAsAdmin("ALTER ROLE pt_tenanta NOLOGIN");

      // then
      assertThat(pool(ROTATED).getUsername()).isEqualTo("pt_tenanta_v2");
      awaitExported(
          client,
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey());
      assertThat(pool(DEFAULT)).isSameAs(defaultPool);
      assertThat(defaultPool.getUsername()).isEqualTo(roleOf(DEFAULT));
      try (final var other = client(DEFAULT)) {
        awaitExported(other, deployAndStart(other, processId));
      }
    }
  }

  @Test
  void shouldKeepServingAndReportTheTenantDegradedWhenTheRotatedPasswordIsWrong() throws Exception {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    try (final var client = client(WRONG)) {
      awaitExported(client, deployAndStart(client, processId));
      final var poolBefore = pool(WRONG);

      // when
      writeSecret(WRONG, "db-password", "not-the-password");

      // then
      Awaitility.await("tenantb is reported degraded")
          .atMost(AWAIT)
          .untilAsserted(
              () -> {
                final var health = secretRefreshHealth();
                assertThat(health.path("status").asText()).isEqualTo("DEGRADED");
                assertThat(health.at("/details/" + WRONG + "/status").asText())
                    .isEqualTo("DEGRADED");
                assertThat(health.at("/details/" + DEFAULT + "/status").asText()).isEqualTo("UP");
                assertThat(health.toString()).doesNotContain("not-the-password");
              });
      assertThat(pool(WRONG)).isSameAs(poolBefore);
      assertThat(poolBefore.getUsername()).isEqualTo(roleOf(WRONG));
      awaitExported(
          client,
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey());
    }
  }

  @Test
  void shouldMoveReadersAndExporterToANewUrlWithoutARestart() throws Exception {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    try (final var client = client(MOVED)) {
      awaitExported(client, deployAndStart(client, processId));
      final var oldPool = pool(MOVED);

      // when
      writeSecret(MOVED, "db-url", urlOf(MOVED, "127.0.0.1") + "&ApplicationName=moved");

      // then
      Awaitility.await("tenantc's connections use the new url")
          .atMost(AWAIT)
          .untilAsserted(
              () -> {
                assertThat(pool(MOVED)).isNotSameAs(oldPool);
                assertThat(oldPool.isClosed()).isTrue();
              });
      assertThat(pool(MOVED).getPoolName()).isEqualTo("camunda-rdbms-tenantc-1");
      awaitExported(
          client,
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey());
      assertThat(sessionCount(roleOf(MOVED), "moved")).isPositive();
    }
  }

  private static TestStandaloneBroker configure() {
    final var broker = TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());
    broker.unifiedConfig().getSecrets().setRefreshInterval(REFRESH_INTERVAL);
    TENANTS_IDS.forEach(
        tenant -> {
          final Consumer<Camunda> secrets =
              camunda -> camunda.getSecrets().getStores().getFile().put("default", store(tenant));
          if (DEFAULT.equals(tenant)) {
            secrets.accept(broker.unifiedConfig());
          } else {
            broker.withPtConfig(tenant, secrets);
          }
        });
    return broker;
  }

  private static Secrets.FileStore store(final String tenant) {
    final var internalConfig = new FileInternalConfig();
    internalConfig.setPath(SECRETS.resolve(tenant).resolve("internal").toString());
    final var store = new Secrets.FileStore();
    store.setPath(SECRETS.resolve(tenant).resolve("jobs").toString());
    store.setInternalConfig(internalConfig);
    return store;
  }

  /** A login role and a schema of its own, and the references that reach them. */
  private static void provision(final String tenant) {
    final var role = roleOf(tenant);
    executeAsAdmin("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(role, role));
    executeAsAdmin("CREATE SCHEMA %s AUTHORIZATION %s".formatted(role, role));
    try {
      Files.createDirectories(SECRETS.resolve(tenant).resolve("jobs"));
      Files.createDirectories(SECRETS.resolve(tenant).resolve("internal"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    writeSecret(tenant, "db-url", urlOf(tenant, POSTGRES.getHost()));
    writeSecret(tenant, "db-username", role);
    writeSecret(tenant, "db-password", role);
  }

  /** {@code default} is a reserved word in PostgreSQL. */
  private static String roleOf(final String tenant) {
    return "pt_" + tenant;
  }

  private static String urlOf(final String tenant, final String host) {
    return "jdbc:postgresql://%s:%d/%s?currentSchema=%s"
        .formatted(
            host,
            POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            DATABASE,
            roleOf(tenant));
  }

  private static void writeSecret(final String tenant, final String name, final String value) {
    try {
      final var internal = SECRETS.resolve(tenant).resolve("internal");
      final var staged = Files.writeString(internal.resolve("." + name), value);
      Files.move(
          staged,
          internal.resolve(name),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private CamundaClient client(final String tenant) {
    return TENANTS.newClientBuilder(BROKER, tenant).build();
  }

  private long deployAndStart(final CamundaClient client, final String processId) {
    TENANTS.deploy(
        client, Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(), processId);
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  /** Written by the tenant's exporter and read back by its readers, both through its pool. */
  private static void awaitExported(final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("process instance %d is exported and readable".formatted(processInstanceKey))
        .atMost(AWAIT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newProcessInstanceSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
  }

  private HikariDataSource pool(final String tenant) throws SQLException {
    return BROKER.bean(RdbmsDataSources.class).dataSourceFor(tenant).unwrap(HikariDataSource.class);
  }

  private JsonNode secretRefreshHealth() throws IOException, InterruptedException {
    final var response =
        HTTP.send(
            HttpRequest.newBuilder(BROKER.actuatorUri("health")).GET().build(),
            BodyHandlers.ofString());
    return OBJECT_MAPPER
        .readTree(response.body())
        .path("components")
        .path("physicalTenantSecretRefresh");
  }

  private static int sessionCount(final String user) {
    return count("SELECT count(*) FROM pg_stat_activity WHERE usename = '%s'".formatted(user));
  }

  private static int sessionCount(final String user, final String applicationName) {
    return count(
        "SELECT count(*) FROM pg_stat_activity WHERE usename = '%s' AND application_name = '%s'"
            .formatted(user, applicationName));
  }

  private static int count(final String sql) {
    try (final var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), ADMIN, ADMIN);
        final var statement = connection.createStatement();
        final var result = statement.executeQuery(sql)) {
      result.next();
      return result.getInt(1);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to run '" + sql + "' on PostgreSQL", e);
    }
  }

  private static void executeAsAdmin(final String sql) {
    try (final var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), ADMIN, ADMIN);
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to run '" + sql + "' on PostgreSQL", e);
    }
  }

  private static void delete(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
