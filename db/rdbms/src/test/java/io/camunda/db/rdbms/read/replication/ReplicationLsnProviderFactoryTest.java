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

class ReplicationLsnProviderFactoryTest {

  @Test
  void shouldCreatePostgresReplicationLsnProvider() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(false);
    when(mapper.hasRequiredPrivileges()).thenReturn(true);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(DefaultReplicationLsnProvider.class);
  }

  @Test
  void shouldFailForPostgresWhenRequiredPrivilegesAreMissing() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(false);
    when(mapper.hasRequiredPrivileges()).thenReturn(false);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when / then
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pg_monitor");
  }

  @Test
  void shouldCreateMssqlReplicationLsnProvider() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mssql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.hasRequiredPrivileges()).thenReturn(true);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(DefaultReplicationLsnProvider.class);
  }

  @Test
  void shouldFailForMssqlWhenRequiredPrivilegesAreMissing() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mssql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.hasRequiredPrivileges()).thenReturn(false);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when / then
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VIEW SERVER STATE");
  }

  @Test
  void shouldCreateAuroraReplicationLsnProviderWhenAuroraDetected() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(true);
    when(mapper.isAuroraGlobalDatabase()).thenReturn(true);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(AuroraReplicationLsnProvider.class);
  }

  @Test
  void shouldFailForAuroraWithoutGlobalDatabase() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(true);
    when(mapper.isAuroraGlobalDatabase()).thenReturn(false);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when / then
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Replication monitoring requires AWS Aurora Global Database");
  }

  @Test
  void shouldCreateAuroraReplicationLsnProviderWhenMysqlAuroraDetected() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mysql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(true);
    when(mapper.isAuroraGlobalDatabase()).thenReturn(true);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(AuroraReplicationLsnProvider.class);
  }

  @Test
  void shouldFailForPlainMysqlWithoutAurora() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("mysql");
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.isAurora()).thenReturn(false);
    final var factory = new ReplicationLsnProviderFactory(vendorDatabaseProperties, mapper);

    // when / then
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Replication monitoring requires AWS Aurora MySQL");
  }

  @Test
  void shouldNotCreateReplicationLsnProviderForUnsupportedDatabase() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("oracle");
    final var factory =
        new ReplicationLsnProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create ReplicationLsnProvider for unknown database id oracle");
  }

  @Test
  void shouldNotCreateReplicationLsnProviderWhenDatabaseIdIsNull() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn(null);
    final var factory =
        new ReplicationLsnProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class));

    // when
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create ReplicationLsnProvider for null database id");
  }
}
