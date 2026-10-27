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
import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
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
public class MappingRuleAppliersTest {

  private MutableProcessingState processingState;

  private MutableMappingRuleState mappingRuleState;
  private MutableRoleState roleState;
  private MutableTenantState tenantState;
  private MutableGroupState groupState;
  private MutableAuthorizationState authorizationState;
  private MutableMembershipState membershipState;
  private MappingRuleDeletedApplier mappingRuleDeletedApplier;
  private MappingRuleUpdatedApplier mappingRuleUpdatedApplier;

  @BeforeEach
  public void setup() {
    mappingRuleState = processingState.getMappingRuleState();
    roleState = processingState.getRoleState();
    tenantState = processingState.getTenantState();
    authorizationState = processingState.getAuthorizationState();
    groupState = processingState.getGroupState();
    membershipState = processingState.getMembershipState();
    mappingRuleDeletedApplier =
        new MappingRuleDeletedApplier(processingState.getMappingRuleState());
    mappingRuleUpdatedApplier =
        new MappingRuleUpdatedApplier(processingState.getMappingRuleState());
  }

  @Test
  void shouldDeleteMappingRule() {
    // given
    final var mappingRuleRecord = createMappingRule();

    // when
    mappingRuleDeletedApplier.applyState(mappingRuleRecord.getMappingRuleKey(), mappingRuleRecord);

    // then
    assertThat(mappingRuleState.get(mappingRuleRecord.getMappingRuleId())).isEmpty();
  }

  @Test
  public void shouldThrowExceptionWhenDeleteNotExistingMappingRule() {
    // given
    final String id = "id";
    final var mappingRuleRecord = new MappingRuleRecord().setMappingRuleId(id);

    // when + then
    assertThatThrownBy(
            () ->
                mappingRuleDeletedApplier.applyState(
                    mappingRuleRecord.getMappingRuleKey(), mappingRuleRecord))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Expected to delete mapping rule with id 'id', but a mapping rule with this id does not exist.");
  }

  @Test
  void shouldUpdateMapping() {
    // given
    final var mappingRuleRecord = createMappingRule();
    final var newClaimName = "new-claim";
    final var newClaimValue = "new-claim-value";
    final var newName = "new-name";
    mappingRuleRecord.setClaimName(newClaimName);
    mappingRuleRecord.setClaimValue(newClaimValue);
    mappingRuleRecord.setName(newName);
    // when
    mappingRuleUpdatedApplier.applyState(mappingRuleRecord.getMappingRuleKey(), mappingRuleRecord);

    // then
    assertThat(mappingRuleState.get(mappingRuleRecord.getMappingRuleId())).isNotEmpty();
    final var persistedMappingRule =
        mappingRuleState.get(mappingRuleRecord.getMappingRuleId()).get();
    assertThat(persistedMappingRule.getClaimName()).isEqualTo(newClaimName);
    assertThat(persistedMappingRule.getClaimValue()).isEqualTo(newClaimValue);
    assertThat(persistedMappingRule.getName()).isEqualTo(newName);
  }

  @Test
  public void shouldThrowExceptionWhenUpdateNotExistingMappingRuleId() {
    // given
    final var mappingRuleRecord = createMappingRule();
    mappingRuleRecord.setMappingRuleId(UUID.randomUUID().toString());
    // when + then
    assertThatThrownBy(
            () ->
                mappingRuleUpdatedApplier.applyState(
                    mappingRuleRecord.getMappingRuleKey(), mappingRuleRecord))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            String.format(
                "Expected to update mapping rule with id '%s', but a mapping rule with this id does not exist.",
                mappingRuleRecord.getMappingRuleId()));
  }

  private MappingRuleRecord createMappingRule() {
    final long mappingRuleKey = 1L;
    final String mappingRuleId = Strings.newRandomValidIdentityId();
    final String claimName = "foo";
    final String claimValue = "bar";
    final var mappingRuleRecord =
        new MappingRuleRecord()
            .setMappingRuleKey(mappingRuleKey)
            .setMappingRuleId(mappingRuleId)
            .setClaimName(claimName)
            .setClaimValue(claimValue)
            .setName(claimName);
    mappingRuleState.create(mappingRuleRecord);
    // create role
    final var role =
        new RoleRecord()
            .setRoleKey(2L)
            .setRoleId(Strings.newRandomValidIdentityId())
            .setEntityId(mappingRuleId)
            .setEntityType(EntityType.MAPPING_RULE);
    roleState.create(role);
    membershipState.insertRelation(
        EntityType.MAPPING_RULE, mappingRuleId, RelationType.ROLE, role.getRoleId());
    // create tenant
    final long tenantKey = 3L;
    final var tenantId = "tenant";
    membershipState.insertRelation(
        EntityType.MAPPING_RULE, mappingRuleId, RelationType.TENANT, tenantId);
    final var tenant =
        new TenantRecord()
            .setTenantId(tenantId)
            .setTenantKey(tenantKey)
            .setEntityId(mappingRuleId)
            .setEntityType(EntityType.MAPPING_RULE);
    tenantState.createTenant(tenant);
    // create group
    final var group =
        new GroupRecord()
            .setGroupKey(4L)
            .setGroupId(Strings.newRandomValidIdentityId())
            .setEntityId(mappingRuleId)
            .setEntityType(EntityType.MAPPING_RULE);
    groupState.create(group);
    membershipState.insertRelation(
        EntityType.MAPPING_RULE, mappingRuleId, RelationType.GROUP, group.getGroupId());
    // create authorization
    authorizationState.create(
        5L,
        new AuthorizationRecord()
            .setPermissionTypes(Set.of(PermissionType.READ))
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("process")
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setOwnerType(AuthorizationOwnerType.MAPPING_RULE)
            .setOwnerId(mappingRuleId));
    return mappingRuleRecord;
  }
}
