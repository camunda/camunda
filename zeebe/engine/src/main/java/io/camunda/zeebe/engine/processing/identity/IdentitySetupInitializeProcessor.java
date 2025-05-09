/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.WILDCARD_PERMISSION;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;

@ExcludeAuthorizationCheck
public final class IdentitySetupInitializeProcessor
    implements TypedRecordProcessor<IdentitySetupRecord> {
  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public IdentitySetupInitializeProcessor(final Writers writers, final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<IdentitySetupRecord> command) {
    final var initializationKey = keyGenerator.nextKey();
    final var setupRecord = command.getValue();

    final var defaultRole = setupRecord.getDefaultRole();
    createDefaultRole(initializationKey, defaultRole);

    final var defaultTenant = setupRecord.getDefaultTenant();
    createDefaultTenant(initializationKey, defaultTenant);

    final var users = setupRecord.getUsers();
    createUsers(initializationKey, users, defaultRole, defaultTenant);

    final var mappings = setupRecord.getMappings();
    createMappings(initializationKey, mappings, defaultRole, defaultTenant);

    addAllPermissions(initializationKey, defaultRole);

    stateWriter.appendFollowUpEvent(
        initializationKey, IdentitySetupIntent.INITIALIZED, setupRecord);
  }

  private void createDefaultRole(final long key, final RoleRecord defaultRole) {
    commandWriter.appendFollowUpCommand(key, RoleIntent.CREATE, defaultRole);
  }

  private void addAllPermissions(final long key, final RoleRecord defaultRole) {
    for (final AuthorizationResourceType resourceType : AuthorizationResourceType.values()) {
      if (resourceType == AuthorizationResourceType.UNSPECIFIED) {
        // We shouldn't add empty permissions for an unspecified resource type
        continue;
      }

      final var record =
          new AuthorizationRecord()
              .setOwnerId(defaultRole.getRoleId())
              .setOwnerType(AuthorizationOwnerType.ROLE)
              .setResourceType(resourceType)
              .setResourceId(WILDCARD_PERMISSION)
              .setPermissionTypes(resourceType.getSupportedPermissionTypes());
      commandWriter.appendFollowUpCommand(key, AuthorizationIntent.CREATE, record);
    }
  }

  private void createDefaultTenant(final long key, final TenantRecord defaultTenant) {
    commandWriter.appendFollowUpCommand(key, TenantIntent.CREATE, defaultTenant);
  }

  private void createUsers(
      final long key,
      final List<UserRecordValue> users,
      final RoleRecord defaultRole,
      final TenantRecord defaultTenant) {
    users.forEach(
        user -> {
          commandWriter.appendFollowUpCommand(key, UserIntent.CREATE, user);
          assignToRole(key, defaultRole.getRoleId(), user.getUsername(), EntityType.USER);
          assignToTenant(key, defaultTenant.getTenantId(), user.getUsername(), EntityType.USER);
        });
  }

  private void createMappings(
      final long key,
      final List<MappingRecordValue> mappings,
      final RoleRecord defaultRole,
      final TenantRecord defaultTenant) {
    mappings.forEach(
        mapping -> {
          commandWriter.appendFollowUpCommand(key, MappingIntent.CREATE, mapping);
          assignToRole(key, defaultRole.getRoleId(), mapping.getMappingId(), EntityType.MAPPING);
          assignToTenant(
              key, defaultTenant.getTenantId(), mapping.getMappingId(), EntityType.MAPPING);
        });
  }

  private void assignToRole(
      final long key, final String roleId, final String entityId, final EntityType entityType) {
    final var record =
        new RoleRecord().setRoleId(roleId).setEntityId(entityId).setEntityType(entityType);
    commandWriter.appendFollowUpCommand(key, RoleIntent.ADD_ENTITY, record);
  }

  private void assignToTenant(
      final long key, final String tenantId, final String entityId, final EntityType entityType) {
    final var record =
        new TenantRecord().setTenantId(tenantId).setEntityId(entityId).setEntityType(entityType);
    commandWriter.appendFollowUpCommand(key, TenantIntent.ADD_ENTITY, record);
  }
}
