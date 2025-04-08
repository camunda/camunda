/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class TenantAppliersTest {

  private MutableProcessingState processingState;

  private MutableMappingState mappingState;
  private MutableTenantState tenantState;
  private MutableUserState userState;
  private MutableGroupState groupState;
  private MutableAuthorizationState authorizationState;
  private TenantDeletedApplier tenantDeletedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;
  private TenantEntityRemovedApplier tenantEntityRemovedApplier;
  private MutableMembershipState membershipState;

  @BeforeEach
  public void setup() {
    mappingState = processingState.getMappingState();
    tenantState = processingState.getTenantState();
    userState = processingState.getUserState();
    groupState = processingState.getGroupState();
    authorizationState = processingState.getAuthorizationState();
    membershipState = processingState.getMembershipState();
    tenantDeletedApplier = new TenantDeletedApplier(processingState.getTenantState());
    tenantEntityAddedApplier = new TenantEntityAddedApplier(processingState);
    tenantEntityRemovedApplier = new TenantEntityRemovedApplier(processingState);
  }

  @Test
  void shouldAddEntityToTenantWithTypeUser() {
    // given
    final long entityKey = UUID.randomUUID().hashCode();
    final long tenantKey = UUID.randomUUID().hashCode();
    final var tenantId = UUID.randomUUID().toString();
    final var username = "username";
    createTenant(tenantKey, tenantId);
    createUser(entityKey, username);

    // when
    associateUserWithTenant(tenantKey, tenantId, username);

    // then
    assertThat(membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT))
        .singleElement()
        .isEqualTo(tenantId);
  }

  @Test
  void shouldAddEntityToTenantWithTypeMapping() {
    // given
    final var mappingId = "mappingId";
    mappingState.create(
        new MappingRecord()
            .setMappingId(mappingId)
            .setClaimName("claimName")
            .setClaimValue("claimValue"));
    final String tenantId = "tenantId";
    final long tenantKey = 11L;
    final var tenantRecord = new TenantRecord().setTenantId(tenantId).setTenantKey(tenantKey);
    tenantState.createTenant(tenantRecord);
    tenantRecord.setEntityId(mappingId).setEntityType(EntityType.MAPPING);

    // when
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(tenantState.getEntityType(tenantId, mappingId).get()).isEqualTo(EntityType.MAPPING);
    final var persistedMapping = mappingState.get(mappingId).get();
    assertThat(persistedMapping.getTenantIdsList()).containsExactly(tenantId);
  }

  @Test
  void shouldAddEntityToTenantWithTypeGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    groupState.create(
        new GroupRecord().setGroupId(groupId).setName("name").setDescription("description"));
    final String tenantId = "tenantId";
    final long tenantKey = 11L;
    final var tenantRecord = new TenantRecord().setTenantId(tenantId).setTenantKey(tenantKey);
    tenantState.createTenant(tenantRecord);
    tenantRecord.setEntityId(groupId).setEntityType(EntityType.GROUP);

    // when
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(
            membershipState.hasRelation(EntityType.GROUP, groupId, RelationType.TENANT, tenantId))
        .isTrue();
  }

  @Test
  void shouldDeleteTenantWithoutEntities() {
    // given
    final long tenantKey = UUID.randomUUID().hashCode();
    final var tenantId = UUID.randomUUID().toString();

    // Create tenant without any entities
    final TenantRecord tenantRecord = createTenant(tenantKey, tenantId);

    // Ensure the tenant exists before deletion
    assertThat(tenantState.getTenantById(tenantId)).isPresent();

    // when
    tenantDeletedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(tenantState.getTenantById(tenantId)).isEmpty();
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            AuthorizationOwnerType.TENANT,
            tenantId,
            AuthorizationResourceType.TENANT,
            PermissionType.DELETE);
    assertThat(resourceIdentifiers).isEmpty();
  }

  @Test
  void shouldRemoveEntityFromTenantWithTypeUser() {
    // given
    final long entityKey = UUID.randomUUID().hashCode();
    final long tenantKey = UUID.randomUUID().hashCode();
    final var tenantId = UUID.randomUUID().toString();
    final var username = "username";
    createTenant(tenantKey, tenantId);
    createUser(entityKey, username);
    associateUserWithTenant(tenantKey, tenantId, username);

    // Ensure the user is associated with the tenant before removal
    assertThat(membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT))
        .singleElement()
        .isEqualTo(tenantId);

    // when
    final var tenantRecord =
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(username)
            .setEntityType(EntityType.USER);
    tenantEntityRemovedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(
            membershipState.hasRelation(EntityType.USER, username, RelationType.TENANT, tenantId))
        .isFalse();
  }

  @Test
  @Disabled(
      "Disabled while mappings are not supported: https://github.com/camunda/camunda/issues/26981")
  void shouldRemoveEntityFromTenantWithTypeMapping() {
    // given
    final var mappingId = "mappingId";
    mappingState.create(
        new MappingRecord()
            .setMappingId(mappingId)
            .setClaimName("claimName")
            .setClaimValue("claimValue"));
    final String tenantId = "tenantId";
    final long tenantKey = 11L;
    final var tenantRecord = new TenantRecord().setTenantId(tenantId).setTenantKey(tenantKey);
    tenantState.createTenant(tenantRecord);
    tenantRecord.setEntityId(mappingId).setEntityType(EntityType.MAPPING);
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);

    // Ensure the mapping is associated with the tenant before removal
    assertThat(tenantState.getEntityType(tenantId, mappingId).get()).isEqualTo(EntityType.MAPPING);
    final var persistedMapping = mappingState.get(mappingId).get();
    assertThat(persistedMapping.getTenantIdsList()).containsExactly(tenantId);

    // when
    tenantEntityRemovedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(tenantState.getEntityType(tenantId, mappingId)).isEmpty();
    final var updatedMapping = mappingState.get(mappingId).get();
    assertThat(updatedMapping.getTenantIdsList()).isEmpty();
  }

  @Test
  void shouldRemoveEntityFromTenantWithTypeGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    groupState.create(
        new GroupRecord().setGroupId(groupId).setName("name").setDescription("description"));
    final String tenantId = "tenantId";
    final long tenantKey = 11L;
    final var tenantRecord = new TenantRecord().setTenantId(tenantId).setTenantKey(tenantKey);
    tenantState.createTenant(tenantRecord);
    tenantRecord.setEntityId(groupId).setEntityType(EntityType.GROUP);
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);

    // Ensure the group is associated with the tenant before removal
    assertThat(
            membershipState.hasRelation(EntityType.GROUP, groupId, RelationType.TENANT, tenantId))
        .isTrue();

    // when
    final var tenantRecordToRemove =
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(groupId)
            .setEntityType(EntityType.GROUP);
    tenantEntityRemovedApplier.applyState(tenantKey, tenantRecordToRemove);

    // then
    assertThat(
            membershipState.hasRelation(EntityType.GROUP, groupId, RelationType.TENANT, tenantId))
        .isFalse();
  }

  private TenantRecord createTenant(final long tenantKey, final String tenantId) {
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setName("Tenant-" + tenantId);
    new TenantCreatedApplier(tenantState).applyState(tenantKey, tenantRecord);
    return tenantRecord;
  }

  private void createUser(final long userKey, final String username) {
    final var userRecord =
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(username)
            .setName("User-" + username)
            .setEmail(username + "@test.com")
            .setPassword("password");
    new UserCreatedApplier(userState).applyState(userKey, userRecord);
  }

  private void associateUserWithTenant(
      final long tenantKey, final String tenantId, final String username) {
    final var tenantRecord =
        new TenantRecord()
            .setTenantId(tenantId)
            .setEntityId(username)
            .setEntityType(EntityType.USER);
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);
  }
}
