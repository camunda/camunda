/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

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
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.tenant.PersistedTenant;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Map;

public class TenantAddEntityProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private static final String TENANT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to add entity to tenant with ID '%s', but no tenant with this ID exists.";
  private final TenantState tenantState;
  private final MappingRuleState mappingRuleState;
  private final GroupState groupState;
  private final RoleState roleState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final MembershipState membershipState;

  public TenantAddEntityProcessor(
      final ProcessingState state,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    tenantState = state.getTenantState();
    mappingRuleState = state.getMappingRuleState();
    groupState = state.getGroupState();
    roleState = state.getRoleState();
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

    final var tenantLookup = getPersistedTenant(record);
    if (tenantLookup.isLeft()) {
      rejectCommand(command, RejectionType.NOT_FOUND, tenantLookup.getLeft());
      return;
    }

    final var persistedTenant = tenantLookup.get();
    final var tenantKey = persistedTenant.getTenantKey();
    record.setTenantKey(tenantKey);

    final var entityId = record.getEntityId();
    final var entityType = record.getEntityType();
    if (!isEntityPresent(command.getAuthorizations(), entityType, entityId)) {
      createEntityNotExistRejectCommand(command, entityId, entityType, tenantId);
      return;
    }

    if (isEntityAssigned(record)) {
      createAlreadyAssignedRejectCommand(command, entityId, entityType, tenantId);
      return;
    }

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.ENTITY_ADDED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.ENTITY_ADDED, record, command);

    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    if (isEntityAssigned(record)) {
      createAlreadyAssignedRejectCommand(
          command, record.getEntityId(), record.getEntityType(), record.getTenantId());
    } else {
      stateWriter.appendFollowUpEvent(command.getKey(), TenantIntent.ENTITY_ADDED, record);
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /** Loads the persisted tenant by the tenant id. */
  private Either<String, PersistedTenant> getPersistedTenant(final TenantRecord record) {
    final var tenantId = record.getTenantId();
    return tenantState
        .getTenantById(tenantId)
        .<Either<String, PersistedTenant>>map(Either::right)
        .orElseGet(() -> Either.left(TENANT_NOT_FOUND_ERROR_MESSAGE.formatted(tenantId)));
  }

  private boolean isEntityPresent(
      final Map<String, Object> authorizations,
      final EntityType entityType,
      final String entityId) {
    return switch (entityType) {
      case USER, CLIENT, GROUP ->
          true; // With simple mapping rules, any username, client id or group can be assigned
      case MAPPING_RULE -> mappingRuleState.get(entityId).isPresent();
      case ROLE -> roleState.getRole(entityId).isPresent();
      default -> false;
    };
  }

  private boolean isEntityAssigned(final TenantRecord record) {
    return membershipState.hasRelation(
        record.getEntityType(), record.getEntityId(), RelationType.TENANT, record.getTenantId());
  }

  private void createEntityNotExistRejectCommand(
      final TypedRecord<TenantRecord> command,
      final String entityId,
      final EntityType entityType,
      final String tenantId) {
    rejectCommand(
        command,
        RejectionType.NOT_FOUND,
        formatErrorMessage(entityType, entityId, tenantId, "doesn't exist"));
  }

  private void createAlreadyAssignedRejectCommand(
      final TypedRecord<TenantRecord> command,
      final String entityId,
      final EntityType entityType,
      final String tenantId) {
    rejectCommand(
        command,
        RejectionType.ALREADY_EXISTS,
        formatErrorMessage(entityType, entityId, tenantId, "is already assigned to the tenant"));
  }

  private String formatErrorMessage(
      final EntityType entityType,
      final String entityId,
      final String tenantId,
      final String reason) {
    final var entityName = entityType.name().toLowerCase();
    return "Expected to add %s with ID '%s' to tenant with ID '%s', but the %s %s."
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
