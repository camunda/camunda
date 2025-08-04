/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;

import io.camunda.zeebe.engine.processing.Rejection;
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
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Map;

public class TenantRemoveEntityProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private final TenantState tenantState;
  private final MappingRuleState mappingRuleState;
  private final GroupState groupState;
  private final MembershipState membershipState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantRemoveEntityProcessor(
      final ProcessingState state,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    tenantState = state.getTenantState();
    mappingRuleState = state.getMappingRuleState();
    groupState = state.getGroupState();
    membershipState = state.getMembershipState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    final var tenantId = record.getTenantId();

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.UPDATE)
            .addResourceId(tenantId);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      rejectCommandWithUnauthorizedError(command, isAuthorized.getLeft());
      return;
    }

    final var persistedTenant = tenantState.getTenantById(tenantId);

    if (persistedTenant.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to remove entity from tenant '%s', but no tenant with this ID exists."
              .formatted(tenantId));
      return;
    }

    if (!validateEntityAssignment(command, tenantId)) {
      return;
    }

    final var tenantKey = persistedTenant.get().getTenantKey();
    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.ENTITY_REMOVED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.ENTITY_REMOVED, record, command);
    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    if (validateEntityAssignment(command, command.getValue().getTenantId())) {
      stateWriter.appendFollowUpEvent(
          command.getKey(), TenantIntent.ENTITY_REMOVED, command.getValue());
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean validateEntityAssignment(
      final TypedRecord<TenantRecord> command, final String tenantId) {
    final var entityType = command.getValue().getEntityType();
    final var entityId = command.getValue().getEntityId();
    if (!isEntityPresent(command.getAuthorizations(), entityType, entityId)) {
      createEntityNotExistRejectCommand(command, entityId, entityType, tenantId);
      return false;
    }
    if (!membershipState.hasRelation(entityType, entityId, RelationType.TENANT, tenantId)) {
      createNotAssignedRejectCommand(command, entityId, entityType, tenantId);
      return false;
    }
    return true;
  }

  private boolean isEntityPresent(
      final Map<String, Object> authorizations,
      final EntityType entityType,
      final String entityId) {
    return entityType != MAPPING_RULE || mappingRuleState.get(entityId).isPresent();
  }

  private void createEntityNotExistRejectCommand(
      final TypedRecord<TenantRecord> command,
      final String entityId,
      final EntityType entityType,
      final String tenantId) {
    rejectCommand(
        command,
        RejectionType.NOT_FOUND,
        formatErrorMessage(entityType, entityId, tenantId, "doesn't exists"));
  }

  private void createNotAssignedRejectCommand(
      final TypedRecord<TenantRecord> command,
      final String entityId,
      final EntityType entityType,
      final String tenantId) {
    rejectCommand(
        command,
        RejectionType.NOT_FOUND,
        formatErrorMessage(entityType, entityId, tenantId, "is not assigned to this tenant"));
  }

  private String formatErrorMessage(
      final EntityType entityType,
      final String entityId,
      final String tenantId,
      final String reason) {
    final var entityName = entityType.name().toLowerCase();
    return "Expected to remove %s with ID '%s' from tenant with ID '%s', but the %s %s."
        .formatted(entityName, entityId, tenantId, entityName, reason);
  }

  private void rejectCommandWithUnauthorizedError(
      final TypedRecord<TenantRecord> command, final Rejection rejection) {
    rejectCommand(command, rejection.type(), rejection.reason());
  }

  private void rejectCommand(
      final TypedRecord<TenantRecord> command,
      final RejectionType type,
      final String errorMessage) {
    rejectionWriter.appendRejection(command, type, errorMessage);
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectionOnCommand(command, type, errorMessage);
    }
  }

  private void distributeCommand(final TypedRecord<TenantRecord> command) {
    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }
}
