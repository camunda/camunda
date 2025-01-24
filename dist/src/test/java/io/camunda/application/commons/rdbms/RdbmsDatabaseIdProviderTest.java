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

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RdbmsDatabaseIdProviderTest {

  @Test
  void shouldReturnOverrideWhenSet() {
    final var dataSource = mock(DataSource.class);
    final var databaseIdProvider = new RdbmsDatabaseIdProvider("h2");

    assertThat(databaseIdProvider.getDatabaseId(dataSource)).isEqualTo("h2");
  }

  @Test
  void shouldReturnOriginalWhenOverrideIsNotSet() throws SQLException {
    final var dataSource = mock(DataSource.class, Mockito.RETURNS_DEEP_STUBS);
    when(dataSource.getConnection().getMetaData().getDatabaseProductName()).thenReturn("H2");

    final var databaseIdProvider = new RdbmsDatabaseIdProvider(null);

    assertThat(databaseIdProvider.getDatabaseId(dataSource)).isEqualTo("h2");
  }

  @Test
  void shouldFailIfInvalidOverride() {
    final var dataSource = mock(DataSource.class, Mockito.RETURNS_DEEP_STUBS);
    final var databaseIdProvider = new RdbmsDatabaseIdProvider("foo");

    assertThatThrownBy(() -> databaseIdProvider.getDatabaseId(dataSource))
        .hasMessageStartingWith("Invalid databaseIdOverride 'foo'");
  }

  @Test
  void shouldFailIfVendorCouldNotBeDetected() throws SQLException {
    final var dataSource = mock(DataSource.class, Mockito.RETURNS_DEEP_STUBS);
    when(dataSource.getConnection().getMetaData().getDatabaseProductName()).thenReturn("Foo");

    final var databaseIdProvider = new RdbmsDatabaseIdProvider(null);

    assertThatThrownBy(() -> databaseIdProvider.getDatabaseId(dataSource))
        .hasMessage("Unable to detect database vendor");
  }
}
