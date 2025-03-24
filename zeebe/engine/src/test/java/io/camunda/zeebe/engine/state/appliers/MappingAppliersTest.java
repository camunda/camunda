/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class MappingAppliersTest {

  private MutableProcessingState processingState;

  private MutableMappingState mappingState;
  private MutableRoleState roleState;
  private MutableTenantState tenantState;
  private MutableGroupState groupState;
  private MutableAuthorizationState authorizationState;
  private MappingDeletedApplier mappingDeletedApplier;
  private MappingUpdatedApplier mappingUpdatedApplier;

  @BeforeEach
  public void setup() {
    mappingState = processingState.getMappingState();
    roleState = processingState.getRoleState();
    tenantState = processingState.getTenantState();
    authorizationState = processingState.getAuthorizationState();
    groupState = processingState.getGroupState();
    mappingDeletedApplier = new MappingDeletedApplier(processingState.getMappingState());
    mappingUpdatedApplier = new MappingUpdatedApplier(processingState.getMappingState());
  }

  @Test
  void shouldDeleteMapping() {
    // given
    final var mappingRecord = createMapping();

    // when
    mappingDeletedApplier.applyState(mappingRecord.getMappingKey(), mappingRecord);

    // then
    assertThat(mappingState.get(mappingRecord.getMappingId())).isEmpty();
  }

  @Test
  public void shouldThrowExceptionWhenDeleteNotExistingMapping() {
    // given
    final String id = "id";
    final var mappingRecord = new MappingRecord().setId(id);

    // when + then
    assertThatThrownBy(
            () -> mappingDeletedApplier.applyState(mappingRecord.getMappingKey(), mappingRecord))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Expected to delete mapping with id 'id', but a mapping with this id does not exist.");
  }

  @Test
  void shouldUpdateMapping() {
    // given
    final var mappingRecord = createMapping();
    final var newClaimName = "new-claim";
    final var newClaimValue = "new-claim-value";
    final var newName = "new-name";
    mappingRecord.setClaimName(newClaimName);
    mappingRecord.setClaimValue(newClaimValue);
    mappingRecord.setName(newName);
    // when
    mappingUpdatedApplier.applyState(mappingRecord.getMappingKey(), mappingRecord);

    // then
    assertThat(mappingState.get(mappingRecord.getMappingId())).isNotEmpty();
    final var updatedMapping = mappingState.get(mappingRecord.getMappingId()).get();
    assertThat(updatedMapping.getClaimName()).isEqualTo(newClaimName);
    assertThat(updatedMapping.getClaimValue()).isEqualTo(newClaimValue);
    assertThat(updatedMapping.getName()).isEqualTo(newName);
  }

  @Test
  public void shouldThrowExceptionWhenUpdateNotExistingMappingId() {
    // given
    final var mappingRecord = createMapping();
    mappingRecord.setId(UUID.randomUUID().toString());
    // when + then
    assertThatThrownBy(
            () -> mappingUpdatedApplier.applyState(mappingRecord.getMappingKey(), mappingRecord))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            String.format(
                "Expected to update mapping with id '%s', but a mapping with this id does not exist.",
                mappingRecord.getMappingId()));
  }

  private MappingRecord createMapping() {
    final long mappingKey = 1L;
    final String mappingId = String.valueOf(mappingKey);
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(mappingKey)
            .setId(mappingId)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(claimName)
            .setId(UUID.randomUUID().toString());
    mappingState.create(mappingRecord);
    // create role
    final long roleKey = 2L;
    mappingState.addRole(mappingKey, roleKey);
    final var role =
        new RoleRecord()
            .setRoleKey(roleKey)
            .setEntityKey(mappingKey)
            .setEntityType(EntityType.MAPPING);
    roleState.create(role);
    roleState.addEntity(role);
    // create tenant
    final long tenantKey = 3L;
    final var tenantId = "tenant";
    mappingState.addTenant(mappingId, tenantId);
    final var tenant =
        new TenantRecord()
            .setTenantId(tenantId)
            .setTenantKey(tenantKey)
            .setEntityId(mappingId)
            .setEntityType(EntityType.MAPPING);
    tenantState.createTenant(tenant);
    tenantState.addEntity(tenant);
    // create group
    final long groupKey = 4L;
    mappingState.addGroup(mappingKey, groupKey);
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setEntityKey(mappingKey)
            .setEntityType(EntityType.MAPPING);
    groupState.create(groupKey, group);
    groupState.addEntity(groupKey, group);
    // create authorization
    authorizationState.create(
        5L,
        new AuthorizationRecord()
            .setPermissionTypes(Set.of(PermissionType.READ))
            .setResourceId("process")
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setOwnerType(AuthorizationOwnerType.MAPPING)
            .setOwnerId(mappingId));
    return mappingRecord;
  }
}
