/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import org.junit.jupiter.api.BeforeEach;
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

  @Test
  void shouldCreateTenant() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final String tenantName = "Tenant One";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName(tenantName);

    // when
    tenantState.createTenant(tenantRecord);

    // then
    final var persistedTenant = tenantState.getTenantById(tenantId);
    assertThat(persistedTenant).isPresent();
    assertThat(persistedTenant.get().getTenantId()).isEqualTo(tenantId);
    assertThat(persistedTenant.get().getName()).isEqualTo(tenantName);
  }

  @Test
  void shouldReturnEmptyOptionalIfTenantNotFound() {
    // when
    final var tenant = tenantState.getTenantById("foo");
    // then
    assertThat(tenant).isEmpty();
  }

  @Test
  void shouldUpdateTenantName() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final String oldName = "Tenant One";
    final String newName = "Updated Tenant";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName(oldName);

    tenantState.createTenant(tenantRecord);

    // when
    final var updatedRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName(newName);
    tenantState.updateTenant(updatedRecord);

    // then
    final var persistedTenant = tenantState.getTenantById(tenantId);
    assertThat(persistedTenant).isPresent();
    assertThat(persistedTenant.get().getName()).isEqualTo(newName);
  }

  @Test
  void shouldDeleteTenant() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var entityId = "entityId";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    tenantState.createTenant(tenantRecord);

    // when
    tenantState.delete(tenantRecord);

    // then
    final var deletedTenant = tenantState.getTenantById(tenantId);
    assertThat(deletedTenant).isEmpty();
  }

  @Test
  void shouldFindTenantById() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    tenantState.createTenant(tenantRecord);

    // when
    final var tenant = tenantState.getTenantById(tenantId);

    // then
    assertThat(tenant).isPresent();
    assertThat(tenant.get().getTenantKey()).isEqualTo(tenantKey);
  }

  @Test
  void shouldReturnNewCopiesOnGet() {
    // given
    final String id = "id";
    tenantState.createTenant(new TenantRecord().setTenantId(id).setTenantKey(123L).setName("name"));

    // when
    final var tenant1 = tenantState.getTenantById(id).get();
    final var tenant2 = tenantState.getTenantById(id).get();

    // then
    assertThat(tenant1).isNotSameAs(tenant2);
  }
}
