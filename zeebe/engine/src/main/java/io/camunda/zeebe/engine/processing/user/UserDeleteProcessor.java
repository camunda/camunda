/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserDeleteProcessor implements DistributedTypedRecordProcessor<UserRecord> {

  private static final String USER_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to delete user with username %s, but a user with this username does not exist";
  private final UserState userState;
  private final AuthorizationState authorizationState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final TenantState tenantState;
  private final MembershipState membershipState;
  private final RoleState roleState;
  private final GroupState groupState;

  public UserDeleteProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    userState = state.getUserState();
    tenantState = state.getTenantState();
    authorizationState = state.getAuthorizationState();
    roleState = state.getRoleState();
    groupState = state.getGroupState();
    membershipState = state.getMembershipState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<UserRecord> command) {
    final var record = command.getValue();
    final String username = record.getUsername();
    final var persistedUser = userState.getUser(username);

    if (persistedUser.isEmpty()) {
      final var rejectionMessage = USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(username);

      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, rejectionMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, rejectionMessage);
      return;
    }

    final var user = persistedUser.get();
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.USER, PermissionType.DELETE)
            .addResourceId(user.getUsername());
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    deleteUser(user);
    responseWriter.writeEventOnCommand(
        user.getUserKey(), UserIntent.DELETED, command.getValue(), command);

    final long distributionKey = keyGenerator.nextKey();
    distributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<UserRecord> command) {
    final var username = command.getValue().getUsername();
    userState
        .getUser(username)
        .ifPresentOrElse(
            this::deleteUser,
            () -> {
              final var message = USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(username);
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, message);
            });

    distributionBehavior.acknowledgeCommand(command);
  }

  private void deleteUser(final PersistedUser user) {
    final var userKey = user.getUserKey();
    final var username = user.getUsername();
    deleteAuthorizations(username);
    for (final var tenantId :
        membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT)) {
      final var tenant = tenantState.getTenantById(tenantId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          tenant.getTenantKey(),
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord()
              .setTenantId(tenantId)
              .setEntityId(user.getUsername())
              .setEntityType(EntityType.USER));
    }

    for (final var roleId :
        membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE)) {
      final var role = roleState.getRole(roleId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          role.getRoleKey(),
          RoleIntent.ENTITY_REMOVED,
          new RoleRecord()
              .setRoleKey(role.getRoleKey())
              .setRoleId(roleId)
              .setEntityId(username)
              .setEntityType(EntityType.USER));
    }

    for (final var groupId :
        membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP)) {
      final var group = groupState.get(groupId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          group.getGroupKey(),
          GroupIntent.ENTITY_REMOVED,
          new GroupRecord()
              .setGroupKey(group.getGroupKey())
              .setGroupId(groupId)
              .setEntityId(username)
              .setEntityType(EntityType.USER));
    }

    stateWriter.appendFollowUpEvent(userKey, UserIntent.DELETED, user.getUser());
  }

  private void deleteAuthorizations(final String username) {
    final var authorizationKeysForGroup =
        authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.USER, username);

    authorizationKeysForGroup.forEach(
        authorizationKey -> {
          final var authorization =
              new AuthorizationRecord()
                  .setAuthorizationKey(authorizationKey)
                  .setResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
