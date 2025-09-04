/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class VariableUpdateProcessor implements DistributedTypedRecordProcessor<VariableRecord> {

  private final VariableBehavior variableBehavior;
  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public VariableUpdateProcessor(
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.variableBehavior = variableBehavior;
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<VariableRecord> command) {
    final var value = command.getValue();
    final var variablePointer = variableBehavior.getVariablePointer(value.getVariableKey());

    if (variablePointer == null) {
      final var errorMessage = "This variable does not exists and thus can not be updated";
      writers.rejection().appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      writers.response().writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }
    if (!isAuthorized(command, variablePointer.getScope())) {
      return;
    }
    value.setScopeKey(variablePointer.getScope());
    value.setName(variablePointer.getName());
    final long distributionKey = keyGenerator.nextKey();
    writers.state().appendFollowUpEvent(value.getVariableKey(), VariableIntent.UPDATED, value);
    writers
        .response()
        .writeEventOnCommand(value.getVariableKey(), VariableIntent.UPDATED, value, command);
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(value.getName())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<VariableRecord> command) {
    final var value = command.getValue();
    final var variablePointer = variableBehavior.getVariablePointer(value.getVariableKey());
    if (variablePointer == null) {
      final var errorMessage = "This variable does not exists and thus can not be updated";
      writers.rejection().appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      writers.response().writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
    } else {
      value.setScopeKey(variablePointer.getScope());
      value.setName(variablePointer.getName());
      writers.state().appendFollowUpEvent(value.getVariableKey(), VariableIntent.UPDATED, value);
      writers
          .response()
          .writeEventOnCommand(value.getVariableKey(), VariableIntent.UPDATED, value, command);
    }
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isAuthorized(final TypedRecord<VariableRecord> command, final long scopeKey) {
    final VariableScope variableScope = variableBehavior.inferScope(scopeKey);
    return switch (variableScope) {
      case CLUSTER -> {
        final var authRequest =
            new AuthorizationRequest(
                command, AuthorizationResourceType.CLUSTER_VARIABLE, PermissionType.UPDATE);
        final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
        if (isAuthorized.isLeft()) {
          final var rejection = isAuthorized.getLeft();
          final String errorMessage =
              RejectionType.NOT_FOUND.equals(rejection.type())
                  ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                      "update cluster variables for element", -1, "such element")
                  : rejection.reason();
          writers.rejection().appendRejection(command, rejection.type(), errorMessage);
          writers.response().writeRejectionOnCommand(command, rejection.type(), errorMessage);
          yield false;
        }
        yield true;
      }
      case UNSUPPORTED -> {
        final String errorMessage =
            "Expected to update variable with key '%s' with an unsupported scopeKey %s. Supported scopeKey are: %s"
                .formatted(command.getKey(), scopeKey, -1);
        writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, errorMessage);
        writers
            .response()
            .writeRejectionOnCommand(command, RejectionType.INVALID_STATE, errorMessage);
        yield false;
      }
    };
  }
}
