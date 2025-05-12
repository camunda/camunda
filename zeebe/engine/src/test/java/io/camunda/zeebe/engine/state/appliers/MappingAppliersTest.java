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

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
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
import io.camunda.zeebe.test.util.Strings;
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
  private MutableMembershipState membershipState;
  private MappingDeletedApplier mappingDeletedApplier;
  private MappingUpdatedApplier mappingUpdatedApplier;

  @BeforeEach
  public void setup() {
    mappingState = processingState.getMappingState();
    roleState = processingState.getRoleState();
    tenantState = processingState.getTenantState();
    authorizationState = processingState.getAuthorizationState();
    groupState = processingState.getGroupState();
    membershipState = processingState.getMembershipState();
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
    assertThat(mappingState.get(mappingRecord.getMappingRuleId())).isEmpty();
  }

  @Test
  public void shouldThrowExceptionWhenDeleteNotExistingMapping() {
    // given
    final String id = "id";
    final var mappingRecord = new MappingRecord().setMappingRuleId(id);

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
    assertThat(mappingState.get(mappingRecord.getMappingRuleId())).isNotEmpty();
    final var updatedMapping = mappingState.get(mappingRecord.getMappingRuleId()).get();
    assertThat(updatedMapping.getClaimName()).isEqualTo(newClaimName);
    assertThat(updatedMapping.getClaimValue()).isEqualTo(newClaimValue);
    assertThat(updatedMapping.getName()).isEqualTo(newName);
  }

  @Test
  public void shouldThrowExceptionWhenUpdateNotExistingMappingRuleId() {
    // given
    final var mappingRecord = createMapping();
    mappingRecord.setMappingRuleId(UUID.randomUUID().toString());
    // when + then
    assertThatThrownBy(
            () -> mappingUpdatedApplier.applyState(mappingRecord.getMappingKey(), mappingRecord))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            String.format(
                "Expected to update mapping with id '%s', but a mapping with this id does not exist.",
                mappingRecord.getMappingRuleId()));
  }

  private MappingRecord createMapping() {
    final long mappingKey = 1L;
    final String mappingRuleId = Strings.newRandomValidIdentityId();
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(mappingKey)
            .setMappingRuleId(mappingRuleId)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(claimName);
    mappingState.create(mappingRecord);
    // create role
    final var role =
        new RoleRecord()
            .setRoleKey(2L)
            .setRoleId(Strings.newRandomValidIdentityId())
            .setEntityId(mappingRuleId)
            .setEntityType(EntityType.MAPPING);
    roleState.create(role);
    membershipState.insertRelation(
        EntityType.MAPPING, mappingRuleId, RelationType.ROLE, role.getRoleId());
    // create tenant
    final long tenantKey = 3L;
    final var tenantId = "tenant";
    membershipState.insertRelation(
        EntityType.MAPPING, mappingRuleId, RelationType.TENANT, tenantId);
    final var tenant =
        new TenantRecord()
            .setTenantId(tenantId)
            .setTenantKey(tenantKey)
            .setEntityId(mappingRuleId)
            .setEntityType(EntityType.MAPPING);
    tenantState.createTenant(tenant);
    // create group
    final var group =
        new GroupRecord()
            .setGroupKey(4L)
            .setGroupId(Strings.newRandomValidIdentityId())
            .setEntityId(mappingRuleId)
            .setEntityType(EntityType.MAPPING);
    groupState.create(group);
    membershipState.insertRelation(
        EntityType.MAPPING, mappingRuleId, RelationType.GROUP, group.getGroupId());
    // create authorization
    authorizationState.create(
        5L,
        new AuthorizationRecord()
            .setPermissionTypes(Set.of(PermissionType.READ))
            .setResourceId("process")
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setOwnerType(AuthorizationOwnerType.MAPPING)
            .setOwnerId(mappingRuleId));
    return mappingRecord;
  }
}
