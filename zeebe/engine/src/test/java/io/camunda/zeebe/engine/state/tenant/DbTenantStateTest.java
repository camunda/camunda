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
  void shouldAddEntityToTenant() {
    // given
    final long tenantKey = 1L;
    final String entityId = "entityId";
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityId(entityId)
            .setName("Tenant One");

    // when
    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(tenantRecord);

    // then
    final var entityType = tenantState.getEntityType(tenantId, entityId);
    assertThat(entityType).isPresent();
    assertThat(entityType.get()).isEqualTo(tenantRecord.getEntityType());
  }

  @Test
  void shouldReturnEmptyEntityTypeIfEntityNotFound() {
    // given
    final long tenantKey = 1L;
    final String entityId = "entityId";
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityId(entityId)
            .setName("Tenant One");

    // when
    tenantState.createTenant(tenantRecord);

    // then
    final var entityType = tenantState.getEntityType(tenantId, entityId);
    assertThat(entityType).isEmpty();
  }

  @Test
  void shouldReturnEntityTypeForExistingTenantAndEntity() {
    // given
    final long tenantKey = 1L;
    final String entityId = "entityId";
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityId(entityId)
            .setName("Tenant One")
            .setEntityType(EntityType.USER);

    // when
    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(tenantRecord);

    // then
    final var entityType = tenantState.getEntityType(tenantId, entityId);
    assertThat(entityType).isPresent();
    assertThat(entityType.get()).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldRemoveEntityFromTenant() {
    // given
    final String entityId1 = "entityId1";
    final String entityId2 = "entityId2";
    final String tenantId = "tenant-1";
    final var tenantRecord = new TenantRecord().setTenantId(tenantId).setName("Tenant One");

    tenantState.createTenant(tenantRecord);

    // Add two entities to the tenant
    final var removeEntity1Record =
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(entityId1)
            .setEntityType(EntityType.USER);
    tenantState.addEntity(removeEntity1Record);
    tenantState.addEntity(
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(entityId2)
            .setEntityType(EntityType.USER));

    // when
    tenantState.removeEntity(removeEntity1Record);

    // then
    // Ensure the first entity is removed
    final var deletedEntity = tenantState.getEntityType(tenantId, entityId1);
    assertThat(deletedEntity).isEmpty();

    // Ensure the second entity still exists
    final var remainingEntityType = tenantState.getEntityType(tenantId, entityId2).get();
    assertThat(remainingEntityType).isEqualTo(EntityType.USER);
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
    tenantState.addEntity(
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(entityId)
            .setEntityType(EntityType.USER));

    // when
    tenantState.delete(tenantRecord);

    // then
    final var deletedTenant = tenantState.getTenantById(tenantId);
    assertThat(deletedTenant).isEmpty();
    final var deletedEntity = tenantState.getEntityType(tenantId, entityId);
    assertThat(deletedEntity).isEmpty();
  }

  @Test
  void shouldReturnEntitiesByType() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");
    final var entityId1 = "user";
    final var entityId2 = "mapping";

    tenantState.createTenant(tenantRecord);
    tenantState.addEntity(
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(entityId1)
            .setEntityType(EntityType.USER));
    tenantState.addEntity(
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(entityId2)
            .setEntityType(EntityType.MAPPING));

    // when
    final var entities = tenantState.getEntitiesByType(tenantId);

    // then
    assertThat(entities.get(EntityType.USER)).containsExactly(entityId1);
    assertThat(entities.get(EntityType.MAPPING)).containsExactly(entityId2);
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
