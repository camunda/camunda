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
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Optional;
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
    final var persistedTenant = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenant).isPresent();
    assertThat(persistedTenant.get().getName()).isEqualTo(newName);
  }

  @Test
  void shouldUpdateTenantId() {
    // given
    final long tenantKey = 1L;
    final String oldTenantId = "tenant-1";
    final String newTenantId = "tenant-2";
    final String tenantName = "Tenant One";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(oldTenantId).setName(tenantName);

    tenantState.createTenant(tenantRecord);

    // when
    final var updatedRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(newTenantId).setName(tenantName);
    tenantState.updateTenant(updatedRecord);

    // then
    // Old tenant ID should not be found
    final var oldKey = tenantState.getTenantKeyById(oldTenantId);
    assertThat(oldKey).isEmpty();

    // New tenant ID should be mapped to the tenantKey
    final var newKey = tenantState.getTenantKeyById(newTenantId);
    assertThat(newKey).isPresent();
    assertThat(newKey.get()).isEqualTo(tenantKey);
    // Get the record created from the persisted tenant
    // to ensure the tenant ID was updated in the persistedTenant
    final Optional<TenantRecord> tenantByKey = tenantState.getTenantByKey(tenantKey);
    assertThat(tenantByKey.get().getTenantId()).isEqualTo(newTenantId);
  }

  @Test
  void shouldAddEntityToTenant() {
    // given
    final long tenantKey = 1L;
    final long entityKey = 100L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityKey(entityKey)
            .setName("Tenant One");

    // when
    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(tenantRecord);

    // then
    final var entityType = tenantState.getEntityType(tenantKey, entityKey);
    assertThat(entityType).isPresent();
    assertThat(entityType.get()).isEqualTo(tenantRecord.getEntityType());
  }

  @Test
  void shouldReturnEmptyEntityTypeIfEntityNotFound() {
    // given
    final long tenantKey = 1L;
    final long entityKey = 999L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityKey(entityKey)
            .setName("Tenant One");

    // when
    tenantState.createTenant(tenantRecord);

    // then
    final var entityType = tenantState.getEntityType(tenantKey, entityKey);
    assertThat(entityType).isEmpty();
  }

  @Test
  void shouldReturnEntityTypeForExistingTenantAndEntity() {
    // given
    final long tenantKey = 1L;
    final long entityKey = 100L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityKey(entityKey)
            .setName("Tenant One")
            .setEntityType(EntityType.USER);

    // when
    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(tenantRecord);

    // then
    final var entityType = tenantState.getEntityType(tenantKey, entityKey);
    assertThat(entityType).isPresent();
    assertThat(entityType.get()).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldRemoveEntityFromTenant() {
    // given
    final long tenantKey = 1L;
    final long entityKey1 = 100L;
    final long entityKey2 = 101L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    tenantState.createTenant(tenantRecord);

    // Add two entities to the tenant
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(entityKey1)
            .setEntityType(EntityType.USER));
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(entityKey2)
            .setEntityType(EntityType.USER));

    // when
    tenantState.removeEntity(tenantKey, entityKey1);

    // then
    // Ensure the first entity is removed
    final var deletedEntity = tenantState.getEntityType(tenantKey, entityKey1);
    assertThat(deletedEntity).isEmpty();

    // Ensure the second entity still exists
    final var remainingEntityType = tenantState.getEntityType(tenantKey, entityKey2).get();
    assertThat(remainingEntityType).isEqualTo(EntityType.USER);
  }
}
