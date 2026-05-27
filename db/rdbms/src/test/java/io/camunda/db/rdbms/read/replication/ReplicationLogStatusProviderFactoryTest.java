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
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import org.junit.jupiter.api.Test;

class ReplicationLogStatusProviderFactoryTest {

  @Test
  void shouldCreatePostgresReplicationLogStatusProvider() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(PostgresReplicationLogStatusProvider.class);
  }

  @Test
  void shouldCreateMssqlReplicationLogStatusProvider() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mssql");
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(MssqlReplicationLogStatusProvider.class);
  }

  @Test
  void shouldNotCreateReplicationLogStatusProviderForUnsupportedDatabase() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("oracle");
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create ReplicationLogStatusProvider for unknown database id oracle");
  }

  @Test
  void shouldNotCreateReplicationLogStatusProviderWhenDatabaseIdIsNull() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn(null);
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create ReplicationLogStatusProvider for null database id");
  }
}
