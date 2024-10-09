/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class DbTenantStateTest {

  private MutableProcessingState processingState;
  private MutableTenantState tenantState;

  @BeforeEach
  public void setup() {
    tenantState = processingState.getTenantState();
  }


  @DisplayName("should create a tenant successfully")
  @Test
  void shouldCreateTenantSuccessfully() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-" + UUID.randomUUID();
    final TenantRecord tenantRecord = new TenantRecord().setTenantId(tenantId);

    // when
    tenantState.createTenant(tenantKey, tenantRecord);

    // then
    final TenantRecord persistedTenant = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenant).isNotNull();
    assertThat(persistedTenant.getTenantId()).isEqualTo(tenantId);

    final Long persistedKey = tenantState.getTenantKeyById(tenantId);
    assertThat(persistedKey).isEqualTo(tenantKey);
  }

  @DisplayName("should return null if no tenant with the given tenantId exists")
  @Test
  void shouldReturnNullIfNoTenantWithTenantIdExists() {
    // when
    final var tenantKey = tenantState.getTenantKeyById("tenant-" + UUID.randomUUID());

    // then
    assertThat(tenantKey).isNull();
  }

  @DisplayName("should create tenant if no tenant with the given tenantId exists")
  @Test
  void shouldCreateTenantIfTenantIdDoesNotExist() {
    // when
    final TenantRecord tenantRecord = new TenantRecord()
        .setTenantId("tenant-" + UUID.randomUUID())
        .setName("Tenant A");

    tenantState.addTenant(1L, tenantRecord);

    // then
    final var persistedTenant = tenantState.getTenantByKey(1L);
    assertThat(persistedTenant).isEqualTo(tenantRecord);
  }

  @DisplayName("should throw an exception when creating a tenant with a key that already exists")
  @Test
  void shouldThrowExceptionIfTenantKeyAlreadyExists() {
    final long tenantKey = 2L;
    final TenantRecord tenantRecord = new TenantRecord()
        .setTenantId("tenant-" + UUID.randomUUID())
        .setName("Tenant B");

    // given
    tenantState.addTenant(tenantKey, tenantRecord);

    // when/then
    assertThatThrownBy(() -> tenantState.addTenant(tenantKey, tenantRecord))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("Key DbLong{" + tenantKey + "} in ColumnFamily TENANTS already exists");
  }

  @DisplayName("should return the correct tenant by tenantId")
  @Test
  void shouldReturnCorrectTenantByTenantId() {
    // given
    final String tenantId = "tenant-" + UUID.randomUUID();
    final TenantRecord tenantRecord = new TenantRecord()
        .setTenantId(tenantId)
        .setName("Tenant C");

    tenantState.addTenant(3L, tenantRecord);

    // when
    final Long tenantKey = tenantState.getTenantKeyById(tenantId);

    // then
    assertThat(tenantKey).isNotNull();
    assertThat(tenantState.getTenantByKey(tenantKey)).isEqualTo(tenantRecord);
  }

  @DisplayName("should update a tenant")
  @Test
  void shouldUpdateATenant() {
    final long tenantKey = 4L;
    final String tenantId = "tenant-" + UUID.randomUUID();
    final String name = "Tenant D";

    final TenantRecord tenantRecord = new TenantRecord()
        .setTenantId(tenantId)
        .setName(name);

    tenantState.addTenant(tenantKey, tenantRecord);

    final var persistedTenantBeforeUpdate = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenantBeforeUpdate.getName()).isEqualTo(name);

    final String updatedName = "Tenant E";
    tenantRecord.setName(updatedName);

    tenantState.updateTenant(tenantKey, tenantRecord);

    final var persistedTenantAfterUpdate = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenantAfterUpdate.getName()).isEqualTo(updatedName);
  }

  @DisplayName("should remove a tenant")
  @Test
  void shouldRemoveATenant() {
    final long tenantKey = 5L;
    final String tenantId = "tenant-" + UUID.randomUUID();
    final TenantRecord tenantRecord = new TenantRecord()
        .setTenantId(tenantId)
        .setName("Tenant F");

    tenantState.addTenant(tenantKey, tenantRecord);

    assertThat(tenantState.getTenantByKey(tenantKey)).isNotNull();

    tenantState.removeTenant(tenantKey);

    assertThat(tenantState.getTenantByKey(tenantKey)).isNull();
    assertThat(tenantState.getTenantKeyById(tenantId)).isNull();
  }

  @DisplayName("should handle removing a non-existent tenant gracefully")
  @Test
  void shouldHandleRemovingNonExistentTenant() {
    // when trying to remove a non-existent tenant
    tenantState.removeTenant(999L);

    // then no exception should be thrown
  }

  @DisplayName("should return null when trying to get a non-existent tenant by key")
  @Test
  void shouldReturnNullForNonExistentTenantByKey() {
    // when
    final var persistedTenant = tenantState.getTenantByKey(999L);

    // then
    assertThat(persistedTenant).isNull();
  }
}
