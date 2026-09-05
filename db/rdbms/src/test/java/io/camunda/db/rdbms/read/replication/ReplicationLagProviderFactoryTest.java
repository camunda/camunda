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

class ReplicationLagProviderFactoryTest {

  @Test
  void shouldDeriveLagProviderFromLsnProviderForMssql() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mssql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.hasRequiredPrivileges()).thenReturn(true);
    final var factory = new ReplicationLagProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(LsnBackedReplicationLagProvider.class);
  }

  @Test
  void shouldDeriveLagProviderFromLsnProviderForPlainPostgres() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(false);
    when(mapper.hasRequiredPrivileges()).thenReturn(true);
    final var factory = new ReplicationLagProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(LsnBackedReplicationLagProvider.class);
  }

  @Test
  void shouldDeriveLagProviderFromLsnProviderForAuroraPostgres() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(true);
    when(mapper.isAuroraGlobalDatabase()).thenReturn(true);
    final var factory = new ReplicationLagProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(LsnBackedReplicationLagProvider.class);
  }

  @Test
  void shouldDeriveLagProviderFromLsnProviderForAuroraMySQL() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mysql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(true);
    when(mapper.isAuroraGlobalDatabase()).thenReturn(true);
    final var factory = new ReplicationLagProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(LsnBackedReplicationLagProvider.class);
  }

  @Test
  void shouldFailForPlainMysqlWithoutAurora() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mysql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(false);
    final var factory = new ReplicationLagProviderFactory(vendorDatabaseProperties, mapper);

    // when / then
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Replication monitoring requires AWS Aurora MySQL");
  }

  @Test
  void shouldFailForUnsupportedDatabase() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("oracle");
    final var factory =
        new ReplicationLagProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when / then
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown database id oracle");
  }
}
