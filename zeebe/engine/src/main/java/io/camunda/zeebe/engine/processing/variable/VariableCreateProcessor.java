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
import java.util.List;

public class VariableCreateProcessor implements DistributedTypedRecordProcessor<VariableRecord> {

  private static final List<String> RESERVED_NAMES =
      List.of(
          "null",
          "true",
          "false",
          "function",
          "if",
          "then",
          "else",
          "for",
          "return",
          "between",
          "instance",
          "of",
          "not",
          "in",
          "and",
          "or",
          "some",
          "every",
          "satisfies");
  private static final String FORBIDDEN_CHARS = "+-*/=><?.";

  private final KeyGenerator keyGenerator;
  private final VariableBehavior variableBehavior;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public VariableCreateProcessor(
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<VariableRecord> command) {
    final var value = command.getValue();

    if (!isAuthorized(command)) {
      return;
    }
    final long key = keyGenerator.nextKey();

    if (!isValidCamundaVariableName(value.getName())) {
      final String errorMessage =
          String.format(
              "Invalid Camunda variable name: '%s'. "
                  + "The name must not start with a digit, contain whitespace, or use any of the following characters: %s. "
                  + "Additionally, variable names cannot be any of the reserved keywords or literals: %s.",
              value.getName(), FORBIDDEN_CHARS, RESERVED_NAMES);
      writers.rejection().appendRejection(command, RejectionType.INVALID_ARGUMENT, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, errorMessage);
    } else if (!variableBehavior.variableExists(value.getScopeKey(), value.getNameBuffer())) {
      writers.state().appendFollowUpEvent(key, VariableIntent.CREATED, value);
      writers.response().writeEventOnCommand(key, VariableIntent.CREATED, value, command);
      commandDistributionBehavior.withKey(key).inQueue(value.getName()).distribute(command);
    } else {
      final var errorMessage = "This variable already exists";
      writers.rejection().appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<VariableRecord> command) {
    final var record = command.getValue();
    if (variableBehavior.variableExists(record.getScopeKey(), record.getNameBuffer())) {
      final var errorMessage = "This variable already exists";
      writers.rejection().appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
    } else {
      writers.state().appendFollowUpEvent(command.getKey(), VariableIntent.CREATED, record);
    }
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isAuthorized(final TypedRecord<VariableRecord> command) {
    final VariableScope variableScope =
        variableBehavior.inferScope(command.getValue().getScopeKey());
    return switch (variableScope) {
      case CLUSTER -> {
        final var authRequest =
            new AuthorizationRequest(
                command, AuthorizationResourceType.CLUSTER_VARIABLE, PermissionType.CREATE);
        final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
        if (isAuthorized.isLeft()) {
          final var rejection = isAuthorized.getLeft();
          final String errorMessage =
              RejectionType.NOT_FOUND.equals(rejection.type())
                  ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                      "create cluster variables for element", -1, "such element")
                  : rejection.reason();
          writers.rejection().appendRejection(command, rejection.type(), errorMessage);
          writers.response().writeRejectionOnCommand(command, rejection.type(), errorMessage);
          yield false;
        }
        yield true;
      }
      case UNSUPPORTED -> {
        final String errorMessage =
            "Expected to create variable with name %s with an unsupported scopeKey %s Supported scopeKey are: %s"
                .formatted(command.getValue().getName(), command.getValue().getScopeKey(), -1);
        writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, errorMessage);
        writers
            .response()
            .writeRejectionOnCommand(command, RejectionType.INVALID_STATE, errorMessage);
        yield false;
      }
    };
  }

  public static boolean isValidCamundaVariableName(final String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    if (Character.isDigit(name.charAt(0))) {
      return false;
    }
    if (name.chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    for (final char c : FORBIDDEN_CHARS.toCharArray()) {
      if (name.indexOf(c) != -1) {
        return false;
      }
    }
    return !RESERVED_NAMES.contains(name);
  }
}
