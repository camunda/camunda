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
  void shouldNotUpdateTenantIdWhenUpdatingName() {
    // given
    final long tenantKey = 1L;
    final String originalTenantId = "tenant-1";
    final String newTenantId = "tenant-2"; // This should not be updated
    final String tenantName = "Original Name";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(originalTenantId)
            .setName(tenantName);

    tenantState.createTenant(tenantRecord);

    // when
    final var updatedRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(newTenantId).setName("New Name");
    tenantState.updateTenant(updatedRecord);

    // then
    // Verify that tenantId has not been updated
    final var persistedTenant = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenant).isPresent();
    assertThat(persistedTenant.get().getTenantId()).isEqualTo(originalTenantId);
    assertThat(persistedTenant.get().getName()).isEqualTo("New Name");
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

  @Test
  void shouldVerifyEntityAssignmentToTenant() {
    // given
    final long tenantKey = 1L;
    final long assignedEntityKey = 100L;
    final long unassignedEntityKey = 200L;
    final String tenantId = "tenant-1";

    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    // Create tenant and add an assigned entity
    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(assignedEntityKey)
            .setEntityType(EntityType.USER));

    // when & then
    // Check that the assigned entity is recognized as assigned to the tenant
    assertThat(tenantState.isEntityAssignedToTenant(assignedEntityKey, tenantKey)).isTrue();

    // Check that an unassigned entity is not recognized as assigned to the tenant
    assertThat(tenantState.isEntityAssignedToTenant(unassignedEntityKey, tenantKey)).isFalse();
  }

  @Test
  void shouldDeleteTenant() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(100L)
            .setEntityType(EntityType.USER));

    // when
    tenantState.delete(tenantRecord);

    // then
    final var deletedTenant = tenantState.getTenantByKey(tenantKey);
    assertThat(deletedTenant).isEmpty();
    final var deletedEntity = tenantState.getEntityType(tenantKey, 100L);
    assertThat(deletedEntity).isEmpty();
    final var tenantKeyById = tenantState.getTenantKeyById(tenantId);
    assertThat(tenantKeyById).isEmpty();
  }

  @Test
  void shouldReturnEntitiesByType() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(100L)
            .setEntityType(EntityType.USER));
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(200L)
            .setEntityType(EntityType.MAPPING));

    // when
    final var entities = tenantState.getEntitiesByType(tenantKey);

    // then
    assertThat(entities.get(EntityType.USER)).containsExactly(100L);
    assertThat(entities.get(EntityType.MAPPING)).containsExactly(200L);
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
}
