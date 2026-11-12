/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RoleDeleteProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  private final RoleState roleState;
  private final RoleValidator roleValidator;
  private final AuthorizationState authorizationState;
  private final MembershipState membershipState;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleDeleteProcessor(
      final ProcessingState state,
      final RoleValidator roleValidator,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = state.getRoleState();
    this.roleValidator = roleValidator;
    authorizationState = state.getAuthorizationState();
    membershipState = state.getMembershipState();
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    final String roleId = record.getRoleId();
    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.ROLE, PermissionType.DELETE, roleId);

    if (isAuthorized.isLeft()) {
      reject(command, isAuthorized.getLeft());
      return;
    }

    final var validation = roleValidator.validateDelete(record);
    if (validation.isLeft()) {
      reject(command, validation.getLeft());
      return;
    }

    final var persistedRole = roleState.getRole(roleId).orElseThrow();
    final var roleKey = persistedRole.getRoleKey();
    record.setRoleKey(roleKey);

    removeMembers(record);
    deleteAuthorizations(record);

    stateWriter.appendFollowUpEvent(roleKey, RoleIntent.DELETED, record);
    responseWriter.writeAcceptedResponseOnCommand(roleKey, RoleIntent.DELETED, record, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    roleValidator
        .validateDelete(command.getValue())
        .ifRightOrLeft(
            record -> {
              removeMembers(record);
              deleteAuthorizations(record);
              stateWriter.appendFollowUpEvent(command.getKey(), RoleIntent.DELETED, record);
            },
            rejection ->
                rejectionWriter.appendRejection(command, rejection.type(), rejection.reason()));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void reject(final TypedRecord<RoleRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }

  private void removeMembers(final RoleRecord record) {
    final var roleId = record.getRoleId();
    membershipState.forEachMember(
        RelationType.ROLE,
        roleId,
        (entityType, entityId) -> {
          stateWriter.appendFollowUpEvent(
              record.getRoleKey(),
              RoleIntent.ENTITY_REMOVED,
              new RoleRecord().setRoleId(roleId).setEntityId(entityId).setEntityType(entityType));
        });
  }

  private void deleteAuthorizations(final RoleRecord record) {
    final var roleId = record.getRoleId();
    final var authorizationKeysForRole =
        authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.ROLE, roleId);

    authorizationKeysForRole.forEach(
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
