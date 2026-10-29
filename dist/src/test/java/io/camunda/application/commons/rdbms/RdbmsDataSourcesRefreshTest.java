/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.application.commons.pt.LazyPhysicalTenantResource.State;
import io.camunda.application.commons.secrets.InternalConfigRefreshers;
import io.camunda.application.commons.secrets.InternalConfigSecrets;
import io.camunda.application.commons.secrets.SecretRefreshHealthIndicator;
import io.camunda.application.commons.secrets.SecretStoreConfiguration;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.secondarystorage.Rdbms;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Rotates the credentials and the URL of a tenant's database through its internal-config store,
 * against H2 databases that check passwords, and checks what the pool every consumer reads from
 * does.
 */
final class RdbmsDataSourcesRefreshTest {

  private static final String DEFAULT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final String USER = "APP";

  @TempDir private Path root;

  private Path internal;
  private String firstUrl;
  private String secondUrl;
  private InternalConfigSecrets secrets;
  private SecretRefreshHealthIndicator health;
  private InternalConfigRefreshers refreshers;
  private RdbmsDataSources dataSources;

  @BeforeEach
  void setUp() throws Exception {
    firstUrl = createDatabase("first", "old");
    secondUrl = createDatabase("second", "old");
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    internal = Files.createDirectory(root.resolve("internal"));
    writeSecret("db-url", firstUrl);
    writeSecret("db-username", USER);
    writeSecret("db-password", "old");

    secrets =
        new SecretStoreConfiguration()
            .internalConfigSecrets(
                resolverFor(
                    Map.of(
                        "camunda.secrets.stores.file.default.path", jobs.toString(),
                        "camunda.secrets.stores.file.default.internal-config.path",
                            internal.toString())));
    health = new SecretRefreshHealthIndicator();
    refreshers = InternalConfigRefreshers.of(secrets, Map.of(DEFAULT, Duration.ofHours(1)), health);

    final var rdbms = new Rdbms();
    rdbms.setUrl("camunda.secrets.db-url");
    rdbms.setUsername("camunda.secrets.db-username");
    rdbms.setPassword("camunda.secrets.db-password");
    dataSources = RdbmsDataSources.of(Map.of(DEFAULT, rdbms), new SimpleMeterRegistry(), secrets);
    dataSources.registerForRefresh(refreshers);
  }

  @AfterEach
  void tearDown() throws SQLException {
    dataSources.close();
    refreshers.close();
    secrets.close();
    shutdown(firstUrl);
    shutdown(secondUrl);
  }

  @Test
  void shouldConnectWithTheReferencedSettings() throws SQLException {
    // when
    final var database = databaseNameOf(dataSources.dataSourceFor(DEFAULT));

    // then
    assertThat(database).isEqualTo("FIRST");
  }

  @Test
  void shouldOpenNewConnectionsWithARotatedPasswordWithoutReplacingThePool() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var pool = dataSource.unwrap(HikariDataSource.class);
    final var held = dataSource.getConnection();
    alterPassword(firstUrl, "new");
    writeSecret("db-password", "new");

    // when
    refresh();

    // then
    assertThat(dataSource.unwrap(HikariDataSource.class)).isSameAs(pool);
    assertThat(databaseNameOf(dataSource)).isEqualTo("FIRST");
    assertThat(held.isValid(1)).isTrue();
    held.close();
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldKeepThePoolAndReportTheTenantDegradedWhenTheRotatedPasswordIsWrong() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var pool = dataSource.unwrap(HikariDataSource.class);
    writeSecret("db-password", "wrong");

    // when
    refresh();

    // then
    assertThat(health.health().getStatus().getCode()).isEqualTo("DEGRADED");
    assertThat(health.health().getDetails().toString()).doesNotContain("wrong");
    assertThat(dataSource.unwrap(HikariDataSource.class)).isSameAs(pool);
    assertThat(databaseNameOf(dataSource)).isEqualTo("FIRST");
  }

  @Test
  void shouldMoveToANewPoolWhenTheUrlChanges() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var oldPool = dataSource.unwrap(HikariDataSource.class);
    writeSecret("db-url", secondUrl);

    // when
    refresh();

    // then
    final var newPool = dataSource.unwrap(HikariDataSource.class);
    assertThat(newPool).isNotSameAs(oldPool);
    assertThat(newPool.getPoolName()).isEqualTo("camunda-rdbms-default-1");
    assertThat(oldPool.isClosed()).isTrue();
    assertThat(databaseNameOf(dataSource)).isEqualTo("SECOND");
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldKeepThePoolWhenTheNewUrlCannotBeReached() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var oldPool = dataSource.unwrap(HikariDataSource.class);
    writeSecret("db-url", "jdbc:h2:mem:missing-" + UUID.randomUUID() + ";IFEXISTS=TRUE");

    // when
    refresh();

    // then
    assertThat(dataSource.unwrap(HikariDataSource.class)).isSameAs(oldPool);
    assertThat(oldPool.isClosed()).isFalse();
    assertThat(databaseNameOf(dataSource)).isEqualTo("FIRST");
    assertThat(health.health().getStatus().getCode()).isEqualTo("DEGRADED");
  }

  @Test
  void shouldRejectAMoveToAnotherDatabaseVendor() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var oldPool = dataSource.unwrap(HikariDataSource.class);
    writeSecret("db-url", "jdbc:postgresql://localhost:1/camunda");

    // when
    refresh();

    // then
    assertThat(dataSource.unwrap(HikariDataSource.class)).isSameAs(oldPool);
    assertThat(health.health().getDetails().toString()).contains("from a h2 database");
  }

  @Test
  void shouldBuildNoPoolWhenTheUrlRotatesBeforeFirstUse() throws Exception {
    // given
    writeSecret("db-url", secondUrl);

    // when
    refresh();

    // then
    assertThat(dataSources.poolState(DEFAULT)).isEqualTo(State.NOT_CREATED);
    assertThat(dataSources.isPoolStarted(DEFAULT)).isFalse();
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    assertThat(databaseNameOf(dataSource)).isEqualTo("SECOND");
    assertThat(dataSource.unwrap(HikariDataSource.class).getPoolName())
        .isEqualTo("camunda-rdbms-default");
  }

  @Test
  void shouldBuildThePoolWithAPasswordRotatedBeforeFirstUse() throws Exception {
    // given
    alterPassword(firstUrl, "new");
    writeSecret("db-password", "new");

    // when
    refresh();

    // then
    assertThat(dataSources.poolState(DEFAULT)).isEqualTo(State.NOT_CREATED);
    assertThat(databaseNameOf(dataSources.dataSourceFor(DEFAULT))).isEqualTo("FIRST");
  }

  @Test
  void shouldReadTheSchemaOverTheRotatedSettingsWithoutBuildingAPool() throws Exception {
    // given
    writeSecret("db-url", secondUrl);
    refresh();

    // when
    final var database = databaseNameOf(dataSources.unpooledDataSourceFor(DEFAULT));

    // then
    assertThat(database).isEqualTo("SECOND");
    assertThat(dataSources.poolState(DEFAULT)).isEqualTo(State.NOT_CREATED);
  }

  @Test
  void shouldCloseWithoutBuildingAPoolBeforeFirstUse() throws Exception {
    // given
    writeSecret("db-url", secondUrl);
    refresh();

    // when
    dataSources.close();

    // then
    assertThat(dataSources.poolState(DEFAULT)).isEqualTo(State.CLOSED);
    assertThatThrownBy(() -> dataSources.dataSourceFor(DEFAULT).getConnection())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCloseThePoolBuiltOnFirstUse() throws Exception {
    // given
    final var pool = dataSources.dataSourceFor(DEFAULT).unwrap(HikariDataSource.class);
    assertThat(databaseNameOf(dataSources.dataSourceFor(DEFAULT))).isEqualTo("FIRST");

    // when
    dataSources.close();

    // then
    assertThat(pool.isClosed()).isTrue();
    assertThat(dataSources.poolState(DEFAULT)).isEqualTo(State.CLOSED);
  }

  @Test
  void shouldCloseThePoolThatReplacedTheFirstOne() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var firstPool = dataSource.unwrap(HikariDataSource.class);
    writeSecret("db-url", secondUrl);
    refresh();
    final var secondPool = dataSource.unwrap(HikariDataSource.class);

    // when
    dataSources.close();

    // then
    assertThat(firstPool.isClosed()).isTrue();
    assertThat(secondPool.isClosed()).isTrue();
  }

  @Test
  void shouldCloseThePoolWhenTheTenantIsRemoved() throws Exception {
    // given
    final var dataSource = dataSources.dataSourceFor(DEFAULT);
    final var pool = dataSource.unwrap(HikariDataSource.class);

    // when
    refreshers.stop(DEFAULT);

    // then
    assertThat(pool.isClosed()).isTrue();
    assertThat(dataSources.poolState(DEFAULT)).isEqualTo(State.CLOSED);
    assertThat(refreshers.forPhysicalTenant(DEFAULT)).isEmpty();
  }

  private void refresh() {
    refreshers.forPhysicalTenant(DEFAULT).orElseThrow().refresh();
  }

  private void writeSecret(final String name, final String value) throws IOException {
    Files.writeString(internal.resolve(name), value);
  }

  private static String databaseNameOf(final DataSource dataSource) throws SQLException {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery("SELECT NAME FROM MARKER")) {
      result.next();
      return result.getString(1);
    }
  }

  private static String createDatabase(final String name, final String password)
      throws SQLException {
    final var url = "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (final var connection = DriverManager.getConnection(url, "sa", "");
        final var statement = connection.createStatement()) {
      statement.execute("CREATE USER " + USER + " PASSWORD '" + password + "' ADMIN");
      statement.execute("CREATE TABLE MARKER (NAME VARCHAR(16))");
      statement.execute("INSERT INTO MARKER VALUES ('" + name.toUpperCase() + "')");
    }
    return url;
  }

  private static void alterPassword(final String url, final String password) throws SQLException {
    try (final var connection = DriverManager.getConnection(url, "sa", "");
        final var statement = connection.createStatement()) {
      statement.execute("ALTER USER " + USER + " SET PASSWORD '" + password + "'");
    }
  }

  private static void shutdown(final String url) throws SQLException {
    try (final var connection = DriverManager.getConnection(url, "sa", "");
        final var statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
  }

  private static PhysicalTenantResolver resolverFor(final Map<String, Object> properties) {
    final var env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", properties));
    final var camunda = new Camunda();
    Binder.get(env).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(env, camunda);
  }
}
