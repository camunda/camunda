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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.collections.MutableBoolean;

@ExcludeAuthorizationCheck
public final class IdentitySetupInitializeProcessor
    implements DistributedTypedRecordProcessor<IdentitySetupRecord> {
  private final RoleState roleState;
  private final UserState userState;
  private final TenantState tenantState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final MappingState mappingState;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedCommandWriter commandWriter;

  public IdentitySetupInitializeProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    tenantState = processingState.getTenantState();
    mappingState = processingState.getMappingState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    commandWriter = writers.command();
  }

  @Override
  public void processNewCommand(final TypedRecord<IdentitySetupRecord> command) {
    final var setupRecord = command.getValue();

    if (!createNewEntities(setupRecord)) {
      rejectionWriter.appendRejection(
          command, RejectionType.ALREADY_EXISTS, "Entities already exist");
      return;
    }

    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, IdentitySetupIntent.INITIALIZED, setupRecord);
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY)
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<IdentitySetupRecord> command) {
    createDistributedEntities(command.getValue());
    stateWriter.appendFollowUpEvent(
        command.getKey(), IdentitySetupIntent.INITIALIZED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean createNewEntities(final IdentitySetupRecord record) {
    final var createdNewEntities = new MutableBoolean(false);
    final var role = record.getDefaultRole();
    roleState
        .getRoleKeyByName(role.getName())
        .ifPresentOrElse(
            role::setRoleKey,
            () -> {
              createdNewEntities.set(true);
              final long roleKey = keyGenerator.nextKey();
              role.setRoleKey(roleKey);
              createRole(role);
              addAllPermissions(role.getRoleKey());
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
                          if (assignEntityToRole(
                              role.getRoleKey(), String.valueOf(userKey), EntityType.USER)) {
                            createdNewEntities.set(true);
                          }
                        },
                        () -> {
                          createdNewEntities.set(true);
                          final long userKey = keyGenerator.nextKey();
                          user.setUserKey(userKey);
                          createUser(user, role.getRoleKey());
                        }));

    final var tenant = record.getDefaultTenant();
    tenantState
        .getTenantById(tenant.getTenantId())
        .ifPresentOrElse(
            t -> tenant.setTenantKey(t.getTenantKey()),
            () -> {
              createdNewEntities.set(true);
              final long tenantKey = keyGenerator.nextKey();
              tenant.setTenantKey(tenantKey);
              createTenant(tenant);
            });

    record.getMappings().stream()
        .map(MappingRecord.class::cast)
        .forEach(
            mapping ->
                mappingState
                    .get(mapping.getClaimName(), mapping.getClaimValue())
                    .map(PersistedMapping::getId)
                    .ifPresentOrElse(
                        mappingId -> {
                          mapping.setId(mappingId);
                          if (assignEntityToRole(
                              role.getRoleKey(), mappingId, EntityType.MAPPING)) {
                            createdNewEntities.set(true);
                          }
                        },
                        () -> {
                          createdNewEntities.set(true);
                          final String mappingId = String.valueOf(keyGenerator.nextKey());
                          mapping.setId(mappingId);
                          createMapping(mapping, role.getRoleKey());
                        }));
    return createdNewEntities.get();
  }

  private void createDistributedEntities(final IdentitySetupRecord record) {
    final var role = record.getDefaultRole();
    if (roleState.getRole(role.getRoleKey()).isEmpty()) {
      createRole(role);
    }

    record.getUsers().stream()
        .map(UserRecord.class::cast)
        .forEach(
            user ->
                userState
                    .getUser(user.getUserKey())
                    .ifPresentOrElse(
                        userKey ->
                            assignEntityToRole(
                                role.getRoleKey(),
                                String.valueOf(userKey.getUserKey()),
                                EntityType.USER),
                        () -> createUser(user, role.getRoleKey())));

    if (tenantState.getTenantById(record.getDefaultTenant().getTenantId()).isEmpty()) {
      createTenant(record.getDefaultTenant());
    }

    record.getMappings().stream()
        .map(MappingRecord.class::cast)
        .forEach(
            mapping ->
                mappingState
                    .get(mapping.getClaimName(), mapping.getClaimValue())
                    .ifPresentOrElse(
                        mappingKey ->
                            assignEntityToRole(
                                role.getRoleKey(), mappingKey.getId(), EntityType.MAPPING),
                        () -> createMapping(mapping, role.getRoleKey())));
  }

  private void createRole(final RoleRecord role) {
    stateWriter.appendFollowUpEvent(role.getRoleKey(), RoleIntent.CREATED, role);
  }

  private void createUser(final UserRecord user, final long roleKey) {
    stateWriter.appendFollowUpEvent(user.getUserKey(), UserIntent.CREATED, user);
    assignEntityToRole(roleKey, String.valueOf(user.getUserKey()), EntityType.USER);
  }

  private void createTenant(final TenantRecord tenant) {
    stateWriter.appendFollowUpEvent(tenant.getTenantKey(), TenantIntent.CREATED, tenant);
  }

  private void createMapping(final MappingRecord mapping, final long roleKey) {
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), MappingIntent.CREATED, mapping);
    assignEntityToRole(roleKey, mapping.getId(), EntityType.MAPPING);
  }

  private boolean assignEntityToRole(
      final long roleKey, final String entityKey, final EntityType entityType) {
    final var isAlreadyAssigned = roleState.getEntityType(roleKey, entityKey).isPresent();
    if (isAlreadyAssigned) {
      return false;
    }

    final var record =
        new RoleRecord().setRoleKey(roleKey).setEntityKey(entityKey).setEntityType(entityType);
    stateWriter.appendFollowUpEvent(roleKey, RoleIntent.ENTITY_ADDED, record);
    return true;
  }

  private void addAllPermissions(final long roleKey) {

    for (final AuthorizationResourceType resourceType : AuthorizationResourceType.values()) {
      if (resourceType == AuthorizationResourceType.UNSPECIFIED) {
        // We shouldn't add empty permissions for an unspecified resource type
        continue;
      }

      // TODO: refactor when Roles use String IDs as unique identifiers
      final var record =
          new AuthorizationRecord()
              .setOwnerId(String.valueOf(roleKey))
              .setOwnerType(AuthorizationOwnerType.ROLE)
              .setResourceType(resourceType)
              .setResourceId(WILDCARD_PERMISSION)
              .setPermissionTypes(resourceType.getSupportedPermissionTypes());

      commandWriter.appendFollowUpCommand(roleKey, AuthorizationIntent.CREATE, record);
    }
  }
}
