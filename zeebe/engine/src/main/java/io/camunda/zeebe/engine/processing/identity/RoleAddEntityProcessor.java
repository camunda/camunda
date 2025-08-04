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
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
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
import java.util.Map;

public class RoleAddEntityProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  public static final String ROLE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to update role with ID '%s', but a role with this ID does not exist.";
  public static final String ENTITY_NOT_FOUND_ERROR_MESSAGE =
      "Expected to add an entity with ID '%s' and type '%s' to role with ID '%s', but the entity doesn't exist.";
  private static final String ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE =
      "Expected to add entity with ID '%s' to role with ID '%s', but the entity is already assigned to this role.";
  private final RoleState roleState;
  private final MappingRuleState mappingRuleState;
  private final MembershipState membershipState;
  private final GroupState groupState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleAddEntityProcessor(
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = processingState.getRoleState();
    mappingRuleState = processingState.getMappingRuleState();
    membershipState = processingState.getMembershipState();
    groupState = processingState.getGroupState();
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
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.UPDATE)
            .addResourceId(record.getRoleId());

    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var persistedRecord = roleState.getRole(record.getRoleId());
    if (persistedRecord.isEmpty()) {
      final var errorMessage = ROLE_NOT_FOUND_ERROR_MESSAGE.formatted(record.getRoleId());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var entityId = record.getEntityId();
    final var entityType = record.getEntityType();
    if (!isEntityPresent(command.getAuthorizations(), entityType, entityId)) {
      final var errorMessage =
          ENTITY_NOT_FOUND_ERROR_MESSAGE.formatted(entityId, entityType, record.getRoleId());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    if (isEntityAssigned(record)) {
      final var errorMessage =
          ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE.formatted(record.getEntityId(), record.getRoleId());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(record.getRoleKey(), RoleIntent.ENTITY_ADDED, record);
    responseWriter.writeEventOnCommand(
        record.getRoleKey(), RoleIntent.ENTITY_ADDED, record, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    if (isEntityAssigned(record)) {
      final var errorMessage =
          ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE.formatted(record.getEntityId(), record.getRoleId());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
    } else {
      stateWriter.appendFollowUpEvent(command.getKey(), RoleIntent.ENTITY_ADDED, record);
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isEntityPresent(
      final Map<String, Object> authorizations,
      final EntityType entityType,
      final String entityId) {
    return switch (entityType) {
      case USER, CLIENT, GROUP ->
          true; // With simple mapping rules, any username, client id or group can be assigned
      case MAPPING_RULE -> mappingRuleState.get(entityId).isPresent();
      default -> false;
    };
  }

  private boolean isEntityAssigned(final RoleRecord record) {
    return membershipState.hasRelation(
        record.getEntityType(), record.getEntityId(), RelationType.ROLE, record.getRoleId());
  }
}
