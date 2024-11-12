/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class RoleUpdateProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  private final RoleState roleState;
  private final KeyGenerator keyGenerator;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleUpdateProcessor(
      final RoleState roleState,
      final KeyGenerator keyGenerator,
      final AuthorizationCheckBehavior authCheckBehavior,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.roleState = roleState;
    this.keyGenerator = keyGenerator;
    this.authCheckBehavior = authCheckBehavior;
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

    final var updatedName = record.getName();
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.UPDATE)
            .addResourceId(persistedRecord.get().getName());
    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              authorizationRequest.getPermissionType(),
              authorizationRequest.getResourceType(),
              "role name '%s'".formatted(persistedRecord.get().getName()));
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
      return;
    }

    if (updatedName.equals(persistedRecord.get().getName())) {
      final var errorMessage =
          "Expected to update role with name '%s', but a role with this name already exists."
              .formatted(updatedName);
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(record.getRoleKey(), RoleIntent.UPDATED, record);
    responseWriter.writeEventOnCommand(record.getRoleKey(), RoleIntent.UPDATED, record, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getRoleKey(), RoleIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
