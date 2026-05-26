/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class AuroraDatabaseDetectorTest {

  @Test
  void shouldDetectAuroraPostgresql() throws Exception {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var dataSource = mock(DataSource.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(dataSource
            .getConnection()
            .createStatement()
            .executeQuery("SELECT aurora_version()")
            .next())
        .thenReturn(true);
    final var detector = new AuroraDatabaseDetector(dataSource, vendorDatabaseProperties);

    // when
    final var isAurora = detector.isAurora();

    // then
    assertThat(isAurora).isTrue();
  }

  @Test
  void shouldDetectAuroraMysql() throws Exception {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mysql");
    final var dataSource = mock(DataSource.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(dataSource
            .getConnection()
            .createStatement()
            .executeQuery("SELECT AURORA_VERSION()")
            .next())
        .thenReturn(true);
    final var detector = new AuroraDatabaseDetector(dataSource, vendorDatabaseProperties);

    // when
    final var isAurora = detector.isAurora();

    // then
    assertThat(isAurora).isTrue();
  }

  @Test
  void shouldReturnFalseForPlainPostgresql() throws Exception {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var dataSource = mock(DataSource.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(dataSource.getConnection().createStatement().executeQuery("SELECT aurora_version()"))
        .thenThrow(new SQLException("function aurora_version() does not exist", "42883"));
    final var detector = new AuroraDatabaseDetector(dataSource, vendorDatabaseProperties);

    // when
    final var isAurora = detector.isAurora();

    // then
    assertThat(isAurora).isFalse();
  }

  @Test
  void shouldReturnFalseForPlainMysql() throws Exception {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mysql");
    final var dataSource = mock(DataSource.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(dataSource.getConnection().createStatement().executeQuery("SELECT AURORA_VERSION()"))
        .thenThrow(new SQLException("FUNCTION AURORA_VERSION does not exist", "42000", 1305));
    final var detector = new AuroraDatabaseDetector(dataSource, vendorDatabaseProperties);

    // when
    final var isAurora = detector.isAurora();

    // then
    assertThat(isAurora).isFalse();
  }

  @Test
  void shouldThrowForUnexpectedSqlError() throws Exception {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var dataSource = mock(DataSource.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(dataSource.getConnection().createStatement().executeQuery("SELECT aurora_version()"))
        .thenThrow(new SQLException("permission denied", "42501"));
    final var detector = new AuroraDatabaseDetector(dataSource, vendorDatabaseProperties);

    // when / then
    assertThatThrownBy(detector::isAurora)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to detect Aurora database for database id postgresql");
  }

  @Test
  void shouldReturnFalseForUnsupportedDatabaseFamily() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("oracle");
    final var dataSource = mock(DataSource.class);
    final var detector = new AuroraDatabaseDetector(dataSource, vendorDatabaseProperties);

    // when
    final var isAurora = detector.isAurora();

    // then
    assertThat(isAurora).isFalse();
    verify(vendorDatabaseProperties).databaseId();
  }
}
