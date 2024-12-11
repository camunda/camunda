/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class MappingDeleteProcessor implements DistributedTypedRecordProcessor<MappingRecord> {

  private static final String MAPPING_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete mapping with key '%s', but a mapping with this key does not exist.";
  private final MappingState mappingState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingDeleteProcessor(
      final MappingState mappingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.mappingState = mappingState;
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
    final long mappingKey = record.getMappingKey();
    final var persistedMapping = mappingState.get(mappingKey);
    if (persistedMapping.isEmpty()) {
      final var errorMessage = MAPPING_NOT_FOUND_ERROR_MESSAGE.formatted(mappingKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(
            command, AuthorizationResourceType.MAPPING_RULE, PermissionType.DELETE);
    if (authCheckBehavior.isAuthorized(authorizationRequest).isLeft()) {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authorizationRequest.getPermissionType(), authorizationRequest.getResourceType());
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
      return;
    }

    deleteMapping(persistedMapping.get());
    responseWriter.writeEventOnCommand(mappingKey, MappingIntent.DELETED, record, command);

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
        .get(record.getMappingKey())
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
    for (final var tenantKey : mapping.getTenantKeysList()) {
      stateWriter.appendFollowUpEvent(
          tenantKey,
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord()
              .setTenantKey(tenantKey)
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
        mappingKey, MappingIntent.DELETED, new MappingRecord().setMappingKey(mappingKey));
  }
}
