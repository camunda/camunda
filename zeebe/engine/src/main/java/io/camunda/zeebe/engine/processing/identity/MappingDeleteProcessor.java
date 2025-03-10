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
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MappingState;
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
    final String id = record.getId();
    final var persistedMapping = mappingState.get(id);
    if (persistedMapping.isEmpty()) {
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

    deleteMapping(persistedMapping.get());
    responseWriter.writeEventOnCommand(
        record.getMappingKey(), MappingIntent.DELETED, record, command);

    final long key = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRecord> command) {
    final var record = command.getValue();
    mappingState
        .get(record.getId())
        .ifPresentOrElse(
            this::deleteMapping,
            () -> {
              final var errorMessage =
                  MAPPING_NOT_FOUND_ERROR_MESSAGE.formatted(record.getMappingKey());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void deleteMapping(final PersistedMapping mapping) {
    final var mappingKey = mapping.getMappingKey();
    deleteAuthorizations(mappingKey);
    for (final var tenantId : mapping.getTenantIdsList()) {
      final var tenant = tenantState.getTenantById(tenantId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          tenant.getTenantKey(),
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord()
              .setTenantKey(tenant.getTenantKey())
              .setTenantId(tenant.getTenantId())
              .setEntityKey(mappingKey)
              .setEntityType(EntityType.MAPPING));
    }
    for (final var roleKey : mapping.getRoleKeysList()) {
      stateWriter.appendFollowUpEvent(
          roleKey,
          RoleIntent.ENTITY_REMOVED,
          new RoleRecord()
              .setRoleKey(roleKey)
              .setEntityKey(mappingKey)
              .setEntityType(EntityType.MAPPING));
    }
    for (final var groupKey : mapping.getGroupKeysList()) {
      stateWriter.appendFollowUpEvent(
          groupKey,
          GroupIntent.ENTITY_REMOVED,
          new GroupRecord()
              .setGroupKey(groupKey)
              .setEntityKey(mappingKey)
              .setEntityType(EntityType.MAPPING));
    }
    stateWriter.appendFollowUpEvent(
        mappingKey,
        MappingIntent.DELETED,
        new MappingRecord().setMappingKey(mappingKey).setId(mapping.getId()));
  }

  private void deleteAuthorizations(final long mappingKey) {
    final var authorizationKeysForMapping =
        authorizationState.getAuthorizationKeysForOwner(
            AuthorizationOwnerType.MAPPING, String.valueOf(mappingKey));

    authorizationKeysForMapping.forEach(
        authorizationKey -> {
          final var authorization = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
