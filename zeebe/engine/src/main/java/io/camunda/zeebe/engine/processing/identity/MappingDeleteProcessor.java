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
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class MappingDeleteProcessor implements DistributedTypedRecordProcessor<MappingRecord> {

  private static final String MAPPING_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete mapping with id '%s', but a mapping with this id does not exist.";
  private final MappingState mappingState;
  private final TenantState tenantState;
  private final AuthorizationState authorizationState;
  private final MembershipState membershipState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingDeleteProcessor(
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    mappingState = processingState.getMappingState();
    tenantState = processingState.getTenantState();
    authorizationState = processingState.getAuthorizationState();
    membershipState = processingState.getMembershipState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<MappingRecord> command) {
    final var record = command.getValue();
    final String id = record.getMappingId();
    final var persistedMappingOptional = mappingState.get(id);
    if (persistedMappingOptional.isEmpty()) {
      final var errorMessage = MAPPING_NOT_FOUND_ERROR_MESSAGE.formatted(id);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(
            command, AuthorizationResourceType.MAPPING_RULE, PermissionType.DELETE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }
    final long key = keyGenerator.nextKey();
    deleteMapping(persistedMappingOptional.get(), key);
    responseWriter.writeEventOnCommand(key, MappingIntent.DELETED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRecord> command) {
    final var record = command.getValue();
    mappingState
        .get(record.getMappingId())
        .ifPresentOrElse(
            persistedMapping -> deleteMapping(persistedMapping, command.getKey()),
            () -> {
              final var errorMessage =
                  MAPPING_NOT_FOUND_ERROR_MESSAGE.formatted(record.getMappingId());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void deleteMapping(final PersistedMapping mapping, final long key) {
    final var mappingId = mapping.getMappingId();
    deleteAuthorizations(mappingId);
    for (final var tenantId :
        membershipState.getMemberships(
            EntityType.MAPPING, mapping.getMappingId(), RelationType.TENANT)) {
      final var tenant = tenantState.getTenantById(tenantId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          tenant.getTenantKey(),
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord()
              .setTenantKey(tenant.getTenantKey())
              .setTenantId(tenant.getTenantId())
              .setEntityId(mapping.getMappingId())
              .setEntityType(EntityType.MAPPING));
    }
    for (final var roleKey :
        membershipState.getMemberships(
            EntityType.MAPPING, mapping.getMappingId(), RelationType.ROLE)) {
      stateWriter.appendFollowUpEvent(
          // TODO: Use the role id instead of the key.
          Long.parseLong(roleKey),
          RoleIntent.ENTITY_REMOVED,
          new RoleRecord()
              // TODO: Use the role id instead of the key.
              .setRoleId(roleKey)
              .setRoleKey(Long.parseLong(roleKey))
              .setEntityId(mappingId)
              .setEntityType(EntityType.MAPPING));
    }
    for (final var groupKey :
        membershipState.getMemberships(
            EntityType.MAPPING, mapping.getMappingId(), RelationType.GROUP)) {
      stateWriter.appendFollowUpEvent(
          Long.parseLong(groupKey),
          GroupIntent.ENTITY_REMOVED,
          new GroupRecord()
              .setGroupKey(Long.parseLong(groupKey))
              .setEntityId(mapping.getMappingId())
              .setEntityType(EntityType.MAPPING));
    }
    stateWriter.appendFollowUpEvent(
        key, MappingIntent.DELETED, new MappingRecord().setMappingId(mapping.getMappingId()));
  }

  private void deleteAuthorizations(final String mappingId) {
    final var authorizationKeysForMapping =
        authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.MAPPING, mappingId);

    authorizationKeysForMapping.forEach(
        authorizationKey -> {
          final var authorization = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
