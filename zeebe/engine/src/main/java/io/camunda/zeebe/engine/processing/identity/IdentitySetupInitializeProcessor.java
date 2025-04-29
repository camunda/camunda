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
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
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
  private final MembershipState membershipState;

  public IdentitySetupInitializeProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    tenantState = processingState.getTenantState();
    mappingState = processingState.getMappingState();
    membershipState = processingState.getMembershipState();
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
        .getRole(role.getRoleId())
        .ifPresentOrElse(
            persistedRole -> role.setRoleKey(persistedRole.getRoleKey()),
            () -> {
              createdNewEntities.set(true);
              final long roleKey = keyGenerator.nextKey();
              role.setRoleKey(roleKey);
              createRole(role);
              addAllPermissions(roleKey, role.getRoleId());
            });

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

    record.getUsers().stream()
        .map(UserRecord.class::cast)
        .forEach(
            user ->
                userState
                    .getUser(user.getUsername())
                    .ifPresentOrElse(
                        persistedUser -> {
                          user.setUserKey(persistedUser.getUserKey());
                          if (assignEntityToRole(
                              role, persistedUser.getUsername(), EntityType.USER)) {
                            createdNewEntities.set(true);
                          }
                          if (assignEntityToTenant(
                              tenant, persistedUser.getUsername(), EntityType.USER)) {
                            createdNewEntities.set(true);
                          }
                        },
                        () -> {
                          createdNewEntities.set(true);
                          final long userKey = keyGenerator.nextKey();
                          user.setUserKey(userKey);
                          createUser(user, role, tenant);
                        }));

    record.getMappings().stream()
        .map(MappingRecord.class::cast)
        .forEach(
            mapping -> {
              final var existingById = mappingState.get(mapping.getMappingId());

              existingById.ifPresentOrElse(
                  persistedMapping -> {
                    mapping.setMappingKey(persistedMapping.getMappingKey());
                    // If claim name or value differs, update the mapping
                    if (!persistedMapping.getClaimName().equals(mapping.getClaimName())
                        || !persistedMapping.getClaimValue().equals(mapping.getClaimValue())) {
                      stateWriter.appendFollowUpEvent(
                          mapping.getMappingKey(), MappingIntent.UPDATED, mapping);
                    }
                    if (assignEntityToRole(role, mapping.getMappingId(), EntityType.MAPPING)) {
                      createdNewEntities.set(true);
                    }
                    if (assignEntityToTenant(tenant, mapping.getMappingId(), EntityType.MAPPING)) {
                      createdNewEntities.set(true);
                    }
                  },
                  () -> {
                    mapping.setMappingKey(keyGenerator.nextKey());
                    createdNewEntities.set(true);
                    createMapping(mapping, role, tenant);
                  });
            });
    return createdNewEntities.get();
  }

  private void createDistributedEntities(final IdentitySetupRecord record) {
    final var role = record.getDefaultRole();
    if (roleState.getRole(role.getRoleId()).isEmpty()) {
      createRole(role);
    }

    final var tenant = record.getDefaultTenant();
    if (tenantState.getTenantById(tenant.getTenantId()).isEmpty()) {
      createTenant(tenant);
    }

    record.getUsers().stream()
        .map(UserRecord.class::cast)
        .forEach(
            user ->
                userState
                    .getUser(user.getUsername())
                    .ifPresentOrElse(
                        persistedUser -> {
                          assignEntityToRole(role, persistedUser.getUsername(), EntityType.USER);
                          assignEntityToTenant(
                              tenant, persistedUser.getUsername(), EntityType.USER);
                        },
                        () -> createUser(user, role, tenant)));

    record.getMappings().stream()
        .map(MappingRecord.class::cast)
        .forEach(
            mapping ->
                mappingState
                    .get(mapping.getMappingId())
                    .ifPresentOrElse(
                        persistedMapping -> {
                          assignEntityToRole(
                              role, persistedMapping.getMappingId(), EntityType.MAPPING);
                          assignEntityToTenant(tenant, mapping.getMappingId(), EntityType.MAPPING);
                        },
                        () -> createMapping(mapping, role, tenant)));
  }

  private void createRole(final RoleRecord role) {
    stateWriter.appendFollowUpEvent(role.getRoleKey(), RoleIntent.CREATED, role);
  }

  private void createUser(final UserRecord user, final RoleRecord role, final TenantRecord tenant) {
    stateWriter.appendFollowUpEvent(user.getUserKey(), UserIntent.CREATED, user);
    assignEntityToRole(role, user.getUsername(), EntityType.USER);
    assignEntityToTenant(tenant, user.getUsername(), EntityType.USER);
  }

  private void createTenant(final TenantRecord tenant) {
    stateWriter.appendFollowUpEvent(tenant.getTenantKey(), TenantIntent.CREATED, tenant);
  }

  private void createMapping(
      final MappingRecord mapping, final RoleRecord role, final TenantRecord tenant) {
    stateWriter.appendFollowUpEvent(mapping.getMappingKey(), MappingIntent.CREATED, mapping);
    assignEntityToRole(role, mapping.getMappingId(), EntityType.MAPPING);
    assignEntityToTenant(tenant, mapping.getMappingId(), EntityType.MAPPING);
  }

  private boolean assignEntityToRole(
      final RoleRecord role, final String entityId, final EntityType entityType) {
    final var roleId = role.getRoleId();
    final var roleKey = role.getRoleKey();
    final var isAlreadyAssigned =
        switch (entityType) {
          case USER, MAPPING ->
              membershipState.hasRelation(entityType, entityId, RelationType.ROLE, roleId);
          default -> throw new IllegalArgumentException("Unsupported entity type: " + entityType);
        };
    if (isAlreadyAssigned) {
      return false;
    }

    final var record =
        new RoleRecord()
            .setRoleKey(roleKey)
            .setRoleId(roleId)
            .setEntityId(entityId)
            .setEntityType(entityType);
    stateWriter.appendFollowUpEvent(roleKey, RoleIntent.ENTITY_ADDED, record);
    return true;
  }

  private boolean assignEntityToTenant(
      final TenantRecord tenant, final String entityId, final EntityType entityType) {
    final var isAlreadyAssigned =
        switch (entityType) {
          case USER, MAPPING ->
              membershipState.hasRelation(
                  entityType, entityId, RelationType.TENANT, tenant.getTenantId());
          default -> throw new IllegalArgumentException("Unsupported entity type: " + entityType);
        };
    if (isAlreadyAssigned) {
      return false;
    }

    final var record =
        new TenantRecord()
            .setTenantId(tenant.getTenantId())
            .setEntityId(entityId)
            .setEntityType(entityType);
    // TODO tenantKey should become a generic initilization key when switching to commands
    //  (https://github.com/camunda/camunda/issues/29810)
    stateWriter.appendFollowUpEvent(tenant.getTenantKey(), TenantIntent.ENTITY_ADDED, record);
    return true;
  }

  private void addAllPermissions(final long key, final String id) {

    for (final AuthorizationResourceType resourceType : AuthorizationResourceType.values()) {
      if (resourceType == AuthorizationResourceType.UNSPECIFIED) {
        // We shouldn't add empty permissions for an unspecified resource type
        continue;
      }

      final var record =
          new AuthorizationRecord()
              .setOwnerId(id)
              .setOwnerType(AuthorizationOwnerType.ROLE)
              .setResourceType(resourceType)
              .setResourceId(WILDCARD_PERMISSION)
              .setPermissionTypes(resourceType.getSupportedPermissionTypes());

      commandWriter.appendFollowUpCommand(key, AuthorizationIntent.CREATE, record);
    }
  }
}
