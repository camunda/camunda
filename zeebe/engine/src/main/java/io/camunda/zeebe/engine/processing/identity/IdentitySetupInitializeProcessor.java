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
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;

@ExcludeAuthorizationCheck
public final class IdentitySetupInitializeProcessor
    implements DistributedTypedRecordProcessor<IdentitySetupRecord> {
  private final RoleState roleState;
  private final UserState userState;
  private final TenantState tenantState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public IdentitySetupInitializeProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    tenantState = processingState.getTenantState();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<IdentitySetupRecord> command) {
    final var key = keyGenerator.nextKey();
    final var setupRecord = command.getValue();
    final var existingEntityKeys = findExistingDefaultEntityKeys(setupRecord);
    setNewEntityKeys(existingEntityKeys, setupRecord);
    initializeDefaultEntities(key, existingEntityKeys, setupRecord);
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY)
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<IdentitySetupRecord> command) {
    final var existingEntities = findExistingDefaultEntityKeys(command.getValue());
    initializeDefaultEntities(command.getKey(), existingEntities, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private DefaultEntityKeys findExistingDefaultEntityKeys(final IdentitySetupRecord record) {
    final var roleKey = roleState.getRoleKeyByName(record.getDefaultRole().getName());
    final var userKey =
        userState.getUser(record.getDefaultUser().getUsername()).map(PersistedUser::getUserKey);
    final var tenantKey = tenantState.getTenantKeyById(record.getDefaultTenant().getTenantId());
    return new DefaultEntityKeys(roleKey, userKey, tenantKey);
  }

  private void setNewEntityKeys(
      final DefaultEntityKeys existingEntityKeys, final IdentitySetupRecord setup) {
    if (existingEntityKeys.role().isEmpty()) {
      setup.getDefaultRole().setRoleKey(keyGenerator.nextKey());
    }
    if (existingEntityKeys.user().isEmpty()) {
      setup.getDefaultUser().setUserKey(keyGenerator.nextKey());
    }
    if (existingEntityKeys.tenant().isEmpty()) {
      setup.getDefaultTenant().setTenantKey(keyGenerator.nextKey());
    }
  }

  private void initializeDefaultEntities(
      final long commandKey,
      final DefaultEntityKeys existingDefaultEntities,
      final IdentitySetupRecord setup) {
    final var roleRecord = setup.getDefaultRole();
    final var userRecord = setup.getDefaultUser();
    final var tenantRecord = setup.getDefaultTenant();

    if (existingDefaultEntities.role().isEmpty()) {
      stateWriter.appendFollowUpEvent(commandKey, RoleIntent.CREATED, roleRecord);
      addAllPermissions(roleRecord.getRoleKey());
    }
    if (existingDefaultEntities.user().isEmpty()) {
      stateWriter.appendFollowUpEvent(commandKey, UserIntent.CREATED, userRecord);
    }
    if (existingDefaultEntities.tenant().isEmpty()) {
      stateWriter.appendFollowUpEvent(commandKey, TenantIntent.CREATED, tenantRecord);
    }

    assignUserToRole(
        commandKey,
        existingDefaultEntities.role().orElse(roleRecord.getRoleKey()),
        existingDefaultEntities.user().orElse(userRecord.getUserKey()));
    stateWriter.appendFollowUpEvent(commandKey, IdentitySetupIntent.INITIALIZED, setup);
  }

  private void assignUserToRole(final long commandKey, final long roleKey, final long userKey) {
    final var isAlreadyAssigned = roleState.getEntityType(roleKey, userKey).isPresent();
    if (isAlreadyAssigned) {
      return;
    }

    final var record =
        new RoleRecord().setRoleKey(roleKey).setEntityKey(userKey).setEntityType(EntityType.USER);
    stateWriter.appendFollowUpEvent(commandKey, RoleIntent.ENTITY_ADDED, record);
  }

  private void addAllPermissions(final long roleKey) {

    for (final AuthorizationResourceType resourceType : AuthorizationResourceType.values()) {
      final var record =
          new AuthorizationRecord().setOwnerKey(roleKey).setOwnerType(AuthorizationOwnerType.ROLE);
      record.setResourceType(resourceType);

      for (final PermissionType permissionType : PermissionType.values()) {
        final var permission =
            new Permission().setPermissionType(permissionType).addResourceId(WILDCARD_PERMISSION);
        record.addPermission(permission);
      }

      stateWriter.appendFollowUpEvent(roleKey, AuthorizationIntent.PERMISSION_ADDED, record);
    }
  }

  record DefaultEntityKeys(Optional<Long> role, Optional<Long> user, Optional<Long> tenant) {}
}
