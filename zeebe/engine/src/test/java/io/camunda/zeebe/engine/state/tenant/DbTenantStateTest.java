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
    final var persistedTenant = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenant).isPresent();
    assertThat(persistedTenant.get().getTenantId()).isEqualTo(tenantId);
    assertThat(persistedTenant.get().getName()).isEqualTo(tenantName);
  }

  @Test
  void shouldReturnEmptyOptionalIfTenantNotFound() {
    // when
    final var tenant = tenantState.getTenantByKey(999L);
    // then
    assertThat(tenant).isEmpty();
  }

  @Test
  void shouldReturnTenantKeyById() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    // when
    tenantState.createTenant(tenantRecord);

    // then
    final var retrievedKey = tenantState.getTenantKeyById(tenantId);
    assertThat(retrievedKey).isPresent();
    assertThat(retrievedKey.get()).isEqualTo(tenantKey);
  }

  @Test
  void shouldNotFindTenantByNonExistingId() {
    // when
    final var tenantKey = tenantState.getTenantKeyById("non-existent-id");
    // then
    assertThat(tenantKey).isEmpty();
  }
}
