/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RoleCreateProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  private static final String ROLE_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create role with ID '%s', but a role with this ID already exists";
  private final RoleState roleState;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final EntityMembershipsRemover membershipsRemover;
  private final SideEffectWriter sideEffectWriter;
  private final MembershipStateAdapter membershipStateAdapter;

  public RoleCreateProcessor(
      final ProcessingState processingState,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final MembershipStateAdapter membershipStateAdapter) {
    roleState = processingState.getRoleState();
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
    membershipsRemover = new EntityMembershipsRemover(processingState, stateWriter);
    sideEffectWriter = writers.sideEffect();
    this.membershipStateAdapter = membershipStateAdapter;
  }

  @Override
  public void processNewCommand(final TypedRecord<RoleRecord> command) {
    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.ROLE, PermissionType.CREATE);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var record = command.getValue();
    final var persistedRole = roleState.getRole(record.getRoleId());
    if (persistedRole.isPresent()) {
      final var errorMessage = ROLE_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getRoleId());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }
    final long key = keyGenerator.nextKey();
    record.setRoleKey(key);

    removeLeftoverMemberships(record.getRoleId());
    stateWriter.appendFollowUpEvent(key, RoleIntent.CREATED, record);
    responseWriter.writeAcceptedResponseOnCommand(key, RoleIntent.CREATED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    roleState
        .getRole(record.getRoleId())
        .ifPresentOrElse(
            persistedRole -> {
              final var errorMessage =
                  ROLE_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getRoleId());
              rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
            },
            () -> {
              removeLeftoverMemberships(record.getRoleId());
              stateWriter.appendFollowUpEvent(command.getKey(), RoleIntent.CREATED, record);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Removes the tenant memberships that are still recorded for the ID of the new role. Only an
   * existing role can be assigned to a tenant, so they can only be left behind by a deleted role
   * with the same ID, and the new role must not inherit them.
   */
  private void removeLeftoverMemberships(final String roleId) {
    if (membershipsRemover.removeRoleAndTenantMemberships(EntityType.ROLE, roleId)) {
      sideEffectWriter.appendSideEffect(
          () -> {
            membershipStateAdapter.invalidateAll();
          });
    }
  }
}
