/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RoleUpdateProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  private final RoleState roleState;
  private final RoleValidator roleValidator;
  private final KeyGenerator keyGenerator;
  private final PermissionsBehavior permissionsBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleUpdateProcessor(
      final RoleState roleState,
      final RoleValidator roleValidator,
      final KeyGenerator keyGenerator,
      final PermissionsBehavior permissionsBehavior,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.roleState = roleState;
    this.roleValidator = roleValidator;
    this.keyGenerator = keyGenerator;
    this.permissionsBehavior = permissionsBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.ROLE, PermissionType.UPDATE, record.getRoleId());
    if (isAuthorized.isLeft()) {
      reject(command, isAuthorized.getLeft());
      return;
    }

    final var validation = roleValidator.validateUpdate(record);
    if (validation.isLeft()) {
      reject(command, validation.getLeft());
      return;
    }

    final var persistedRole = roleState.getRole(record.getRoleId()).orElseThrow();
    record.setRoleKey(persistedRole.getRoleKey());
    stateWriter.appendFollowUpEvent(record.getRoleKey(), RoleIntent.UPDATED, record);
    responseWriter.writeAcceptedResponseOnCommand(
        record.getRoleKey(), RoleIntent.UPDATED, record, command);

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

  private void reject(final TypedRecord<RoleRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }
}
