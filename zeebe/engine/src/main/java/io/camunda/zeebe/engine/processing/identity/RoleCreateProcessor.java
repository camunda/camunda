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
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class RoleCreateProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  private static final String ROLE_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create role with ID '%s', but a role with this ID already exists";
  private final RoleState roleState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleCreateProcessor(
      final RoleState roleState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.roleState = roleState;
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<RoleRecord> command) {
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.CREATE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var record = command.getValue();
    final var persistedRole = roleState.getRole(record.getRoleId());
    if (persistedRole.isPresent()) {
      final var errorMessage = ROLE_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getRoleId());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }
    final long key = keyGenerator.nextKey();
    record.setRoleKey(key);

    stateWriter.appendFollowUpEvent(key, RoleIntent.CREATED, record);
    responseWriter.writeEventOnCommand(key, RoleIntent.CREATED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    roleState
        // TODO replace with roleId (https://github.com/camunda/camunda/issues/30116)
        .getRole(record.getRoleKey())
        .ifPresentOrElse(
            persistedRole -> {
              final var errorMessage =
                  ROLE_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getRoleId());
              rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
            },
            () -> stateWriter.appendFollowUpEvent(command.getKey(), RoleIntent.CREATED, record));

    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
