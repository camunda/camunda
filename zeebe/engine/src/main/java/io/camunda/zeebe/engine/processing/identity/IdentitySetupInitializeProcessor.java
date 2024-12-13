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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
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

@ExcludeAuthorizationCheck
public final class IdentitySetupInitializeProcessor
    implements DistributedTypedRecordProcessor<IdentitySetupRecord> {
  private final RoleState roleState;
  private final UserState userState;
  private final TenantState tenantState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final TypedRejectionWriter rejectionWriter;

  public IdentitySetupInitializeProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    tenantState = processingState.getTenantState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<IdentitySetupRecord> command) {
    final var setupRecord = command.getValue();
    final var key = keyGenerator.nextKey();

    createNewEntities(key, setupRecord);

    stateWriter.appendFollowUpEvent(key, IdentitySetupIntent.INITIALIZED, setupRecord);
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY)
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<IdentitySetupRecord> command) {
    createDistributedEntities(command.getKey(), command.getValue());
    stateWriter.appendFollowUpEvent(
        command.getKey(), IdentitySetupIntent.INITIALIZED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void createNewEntities(final long commandKey, final IdentitySetupRecord record) {
    final var role = record.getDefaultRole();
    roleState
        .getRoleKeyByName(role.getName())
        .ifPresentOrElse(
            role::setRoleKey,
            () -> {
              final long roleKey = keyGenerator.nextKey();
              role.setRoleKey(roleKey);
              createRole(commandKey, role);
            });

    record.getUsers().stream()
        .map(UserRecord.class::cast)
        .forEach(
            user ->
                userState
                    .getUser(user.getUsername())
                    .map(PersistedUser::getUserKey)
                    .ifPresentOrElse(
                        userKey -> {
                          user.setUserKey(userKey);
                          assignUserToRole(commandKey, role.getRoleKey(), userKey);
                        },
                        () -> {
                          final long userKey = keyGenerator.nextKey();
                          user.setUserKey(userKey);
                          createUser(commandKey, user, role.getRoleKey());
                        }));

    final var tenant = record.getDefaultTenant();
    tenantState
        .getTenantKeyById(tenant.getTenantId())
        .ifPresentOrElse(
            tenant::setTenantKey,
            () -> {
              final long tenantKey = keyGenerator.nextKey();
              tenant.setTenantKey(tenantKey);
              createTenant(commandKey, tenant);
            });
  }

  private void createDistributedEntities(final long commandKey, final IdentitySetupRecord record) {
    final var role = record.getDefaultRole();
    if (roleState.getRole(role.getRoleKey()).isEmpty()) {
      createRole(commandKey, role);
    }

    record.getUsers().stream()
        .map(UserRecord.class::cast)
        .forEach(
            user ->
                userState
                    .getUser(user.getUserKey())
                    .ifPresentOrElse(
                        userKey ->
                            assignUserToRole(commandKey, role.getRoleKey(), userKey.getUserKey()),
                        () -> {
                          createUser(commandKey, user, role.getRoleKey());
                        }));

    if (tenantState.getTenantByKey(record.getDefaultTenant().getTenantKey()).isEmpty()) {
      createTenant(commandKey, record.getDefaultTenant());
    }
  }

  private void createRole(final long commandKey, final RoleRecord role) {
    stateWriter.appendFollowUpEvent(commandKey, RoleIntent.CREATED, role);
    addAllPermissions(role.getRoleKey());
  }

  private void createUser(final long commandKey, final UserRecord user, final long roleKey) {
    stateWriter.appendFollowUpEvent(commandKey, UserIntent.CREATED, user);
    assignUserToRole(commandKey, roleKey, user.getUserKey());
  }

  private void createTenant(final long commandKey, final TenantRecord tenant) {
    stateWriter.appendFollowUpEvent(commandKey, TenantIntent.CREATED, tenant);
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
}
