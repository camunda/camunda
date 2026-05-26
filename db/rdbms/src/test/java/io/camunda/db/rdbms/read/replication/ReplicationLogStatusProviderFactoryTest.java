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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
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
    final var auroraDatabaseDetector = mock(AuroraDatabaseDetector.class);
    when(auroraDatabaseDetector.isAurora()).thenReturn(false);
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class), auroraDatabaseDetector);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(PostgresReplicationLogStatusProvider.class);
  }

  @Test
  void shouldCreateAuroraPostgresqlReplicationLogStatusProvider() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var auroraDatabaseDetector = mock(AuroraDatabaseDetector.class);
    when(auroraDatabaseDetector.isAurora()).thenReturn(true);
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class), auroraDatabaseDetector);

    // when
    final var provider = factory.create();

    // then
    assertThat(provider).isInstanceOf(AuroraPostgresqlReplicationLogStatusProvider.class);
  }

  @Test
  void shouldDetectPostgresVariantOnlyOnce() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("postgresql");
    final var auroraDatabaseDetector = mock(AuroraDatabaseDetector.class);
    when(auroraDatabaseDetector.isAurora()).thenReturn(false);
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, mock(ReplicationStatusMapper.class), auroraDatabaseDetector);

    // when
    final var firstProvider = factory.create();
    final var secondProvider = factory.create();

    // then
    assertThat(secondProvider).isSameAs(firstProvider);
    org.mockito.Mockito.verify(auroraDatabaseDetector, times(1)).isAurora();
  }

  @Test
  void shouldNotCreateReplicationLogStatusProviderForUnsupportedDatabase() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn("oracle");
    final var replicationStatusMapper = mock(ReplicationStatusMapper.class);
    final var auroraDatabaseDetector = mock(AuroraDatabaseDetector.class);
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties, replicationStatusMapper, auroraDatabaseDetector);

    // when
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create ReplicationLogStatusProvider for unknown database id oracle");

    // then
    verifyNoInteractions(replicationStatusMapper, auroraDatabaseDetector);
  }

  @Test
  void shouldNotCreateReplicationLogStatusProviderWhenDatabaseIdIsNull() {
    // given
    final var vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.databaseId()).thenReturn(null);
    final var factory =
        new ReplicationLogStatusProviderFactory(
            vendorDatabaseProperties,
            mock(ReplicationStatusMapper.class),
            mock(AuroraDatabaseDetector.class));

    // when
    assertThatThrownBy(factory::create)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create ReplicationLogStatusProvider for null database id");
  }
}
