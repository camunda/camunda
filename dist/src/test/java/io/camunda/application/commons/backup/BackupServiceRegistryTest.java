/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.application.commons.backup.BackupServiceRegistry.PhysicalTenantBackup;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupServiceRegistryTest {

  private static PhysicalTenantBackup backup(final String physicalTenantId) {
    return new PhysicalTenantBackup(
        physicalTenantId, mock(BackupService.class), mock(BackupRepositoryProps.class));
  }

  @Test
  public void shouldIterateInConfigurationOrder() {
    // given
    final var defaultBackup = backup("default");
    final var tenantBackup = backup("tenant1");

    // when
    final var registry = new BackupServiceRegistry(List.of(defaultBackup, tenantBackup));

    // then
    assertThat(registry.physicalTenantBackups()).containsExactly(defaultBackup, tenantBackup);
  }

  @Test
  public void shouldLookUpByPhysicalTenantId() {
    // given
    final var tenantBackup = backup("tenant1");
    final var registry = new BackupServiceRegistry(List.of(backup("default"), tenantBackup));

    // when / then
    assertThat(registry.forPhysicalTenant("tenant1")).isSameAs(tenantBackup);
  }

  @Test
  public void shouldRejectUnknownPhysicalTenantId() {
    // given
    final var registry = new BackupServiceRegistry(List.of(backup("default")));

    // when / then
    assertThatThrownBy(() -> registry.forPhysicalTenant("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  public void shouldBeEmptyWhenNoPhysicalTenantConfigured() {
    // given / when
    final var registry = new BackupServiceRegistry(List.of());

    // then
    assertThat(registry.isEmpty()).isTrue();
    assertThat(registry.physicalTenantBackups()).isEmpty();
  }
}
