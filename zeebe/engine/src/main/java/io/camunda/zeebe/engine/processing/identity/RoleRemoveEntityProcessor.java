/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class RoleRemoveEntityProcessor implements DistributedTypedRecordProcessor<RoleRecord> {
  private static final String ENTITY_NOT_ASSIGNED_ERROR_MESSAGE =
      "Expected to remove entity with id '%s' from role with key '%s', but the entity is not assigned to this role.";
  private final RoleState roleState;
  private final UserState userState;
  private final MappingState mappingState;
  private final MembershipState membershipState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleRemoveEntityProcessor(
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();
    membershipState = processingState.getMembershipState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    final var persistedRecord = roleState.getRole(record.getRoleKey());
    if (persistedRecord.isEmpty()) {
      final var errorMessage =
          "Expected to update role with key '%s', but a role with this key does not exist."
              .formatted(record.getRoleKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.UPDATE)
            .addResourceId(persistedRecord.get().getName());
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var entityId = record.getEntityId();
    final var entityType = record.getEntityType();
    if (!isEntityPresent(entityId, entityType)) {
      final var errorMessage =
          "Expected to remove an entity with id '%s' and type '%s' from role with key '%s', but the entity doesn't exist."
              .formatted(entityId, entityType, record.getRoleKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(record.getRoleKey(), RoleIntent.ENTITY_REMOVED, record);
    responseWriter.writeEventOnCommand(
        record.getRoleKey(), RoleIntent.ENTITY_REMOVED, record, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    final var isAssigned =
        switch (record.getEntityType()) {
          case USER ->
              membershipState.hasRelation(
                  EntityType.USER,
                  record.getEntityId(),
                  RelationType.ROLE,
                  // TODO: Use role id instead of key
                  Long.toString(record.getRoleKey()));
          default -> roleState.getEntityType(record.getRoleKey(), record.getEntityId()).isPresent();
        };

    if (isAssigned) {
      stateWriter.appendFollowUpEvent(
          command.getKey(), RoleIntent.ENTITY_REMOVED, command.getValue());
    } else {
      final var errorMessage =
          ENTITY_NOT_ASSIGNED_ERROR_MESSAGE.formatted(record.getEntityId(), record.getRoleKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
    }
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isEntityPresent(final String entityId, final EntityType entityType) {
    return switch (entityType) {
      case USER -> userState.getUser(entityId).isPresent();
      case MAPPING -> mappingState.get(entityId).isPresent();
      default -> false;
    };
  }
}
