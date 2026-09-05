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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.configuration.Rdbms;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

/**
 * Covers how a physical tenant's database vendor is decided. The point of the class under test is
 * that the answer normally costs no connection, which is why {@link RdbmsVendorIdProvider#resolve}
 * is given no {@link DataSource} to reach for in the first place.
 */
class RdbmsVendorIdProviderTest {

  private static final String TENANT_A = "tenant-a";

  /** A url whose vendor this application cannot work out on its own; jTDS is the real instance. */
  private static final String UNRECOGNIZED_URL = "jdbc:jtds:sqlserver://localhost:1433/camunda";

  private static DataSource reporting(final String productName) throws SQLException {
    final var dataSource = mock(DataSource.class, Mockito.RETURNS_DEEP_STUBS);
    when(dataSource.getConnection().getMetaData().getDatabaseProductName()).thenReturn(productName);
    return dataSource;
  }

  private static Rdbms rdbms(final String url) {
    final var rdbms = new Rdbms();
    rdbms.setUrl(url);
    return rdbms;
  }

  // ---- step 1: the explicit override ----

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgresql", "oracle", "mariadb", "mysql", "mssql"})
  void shouldUseTheConfiguredVendorId(final String configured) {
    // given - a url whose vendor could not be worked out on its own
    final var rdbms = rdbms(UNRECOGNIZED_URL);
    rdbms.setDatabaseVendorId(configured);

    // when / then
    assertThat(RdbmsVendorIdProvider.resolve(TENANT_A, rdbms)).contains(configured);
  }

  @Test
  void shouldRejectAConfiguredVendorIdThisApplicationHasNoPropertiesFor() {
    // given
    final var rdbms = rdbms("jdbc:h2:mem:whatever");
    rdbms.setDatabaseVendorId("db2");

    // when / then - naming the tenant and the legal values, since nothing else in the log says
    // which of several tenants carries the bad value
    assertThatThrownBy(() -> RdbmsVendorIdProvider.resolve(TENANT_A, rdbms))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'db2'")
        .hasMessageContaining(TENANT_A)
        .hasMessageContaining(RdbmsVendorIdProvider.VENDOR_ID_PROPERTY)
        .hasMessageContaining("h2, postgresql, oracle, mariadb, mysql, mssql");
  }

  // ---- step 2: the jdbc url ----

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "jdbc:h2:mem:camunda, h2",
    "jdbc:postgresql://localhost:5432/camunda, postgresql",
    "jdbc:oracle:thin:@localhost:1521/camunda, oracle",
    "jdbc:mariadb://localhost:3306/camunda, mariadb",
    // the one case where the url disagrees with what the server would have said: a MariaDB server
    // reached over a jdbc:mysql:// url stays 'mysql'. Accepted, and the reason the log line that
    // reports this step names the override property
    "jdbc:mysql://localhost:3306/camunda, mysql",
    "jdbc:sqlserver://localhost:1433;databaseName=camunda, mssql",
    "jdbc:aws-wrapper:postgresql://localhost:5432/camunda, postgresql",
    "jdbc:aws-wrapper:mysql://localhost:3306/camunda, mysql",
  })
  void shouldResolveTheVendorIdFromTheJdbcUrl(final String url, final String expectedVendorId) {
    // given / when / then - no data source is passed, so a tenant whose database is unreachable
    // resolves exactly as one that is up
    assertThat(RdbmsVendorIdProvider.resolve(TENANT_A, rdbms(url))).contains(expectedVendorId);
  }

  @Test
  void shouldNotResolveAUrlPrefixSpringDoesNotMap() {
    // given / when / then - the empty answer is what sends the caller to the database, and the
    // only case in which building a tenant needs its database at all
    assertThat(RdbmsVendorIdProvider.resolve(TENANT_A, rdbms(UNRECOGNIZED_URL))).isEmpty();
  }

  // ---- step 3: the connection fallback ----

  @Test
  void shouldFallBackToTheDatabaseProductName() {
    // given - a live database behind a url this application cannot classify: such a deployment
    // must keep starting rather than be told to set a property it never needed
    try (final var database = h2()) {

      // when / then
      assertThat(RdbmsVendorIdProvider.fromDatabaseProductName(database)).contains("h2");
    }
  }

  @Test
  void shouldMatchAProductNameThatCarriesMoreThanTheVendorName() throws SQLException {
    // given - the reason MyBatis' provider is reused rather than DatabaseDriver.fromProductName:
    // the latter compares for equality and would stop resolving exactly the deployments this
    // fallback serves
    final var dataSource = reporting("Microsoft SQL Server 2019");

    // when / then
    assertThat(RdbmsVendorIdProvider.fromDatabaseProductName(dataSource)).contains("mssql");
  }

  @Test
  void shouldNotResolveAProductNameThisApplicationHasNoPropertiesFor() throws SQLException {
    // given
    final var dataSource = reporting("Db2");

    // when / then
    assertThat(RdbmsVendorIdProvider.fromDatabaseProductName(dataSource)).isEmpty();
  }

  @Test
  void shouldReportADatabaseItCannotReach() throws SQLException {
    // given
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

    // when / then - the caller turns this into a configuration error naming the tenant
    assertThatThrownBy(() -> RdbmsVendorIdProvider.fromDatabaseProductName(dataSource))
        .rootCause()
        .isInstanceOf(SQLException.class);
  }

  private static HikariDataSource h2() {
    final var dataSource = new HikariDataSource();
    dataSource.setJdbcUrl("jdbc:h2:mem:vendor-id-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");
    return dataSource;
  }
}
