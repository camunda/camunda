/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;

public final class VariableNameLengthValidator {

  private static final String VARIABLE_NAME_LENGTH_EXCEEDED_ERROR_MESSAGE =
      "Expected variable names to be no longer than %d characters, but found one with length %d.";
  private static final String INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE =
      "Expected variables to be valid msgpack, but it could not be read: '%s'";

  private VariableNameLengthValidator() {}

  public static Either<Rejection, Void> validateVariableNameLength(
      final DirectBuffer variablesBuffer) {
    return validateVariableNameLength(
        variablesBuffer, EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH);
  }

  public static Either<Rejection, Void> validateVariableNameLength(
      final DirectBuffer variablesBuffer, final int maxNameFieldLength) {
    if (isEmpty(variablesBuffer)) {
      return Either.right(null);
    }

    final java.util.Map<String, Object> variables;
    try {
      variables = MsgPackConverter.convertToMap(variablesBuffer);
    } catch (final RuntimeException exception) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE.formatted(exception.getMessage())));
    }

    if (variables == null || variables.isEmpty()) {
      return Either.right(null);
    }

    for (final String variableName : variables.keySet()) {
      if (variableName != null && variableName.length() > maxNameFieldLength) {
        return Either.left(variableNameTooLongError(maxNameFieldLength, variableName.length()));
      }
    }

    return Either.right(null);
  }

  private static boolean isEmpty(final DirectBuffer variablesBuffer) {
    return variablesBuffer == null || variablesBuffer.capacity() == 0;
  }

  private static Rejection variableNameTooLongError(
      final int maxNameFieldLength, final int variableNameLength) {
    return new Rejection(
        RejectionType.INVALID_ARGUMENT,
        VARIABLE_NAME_LENGTH_EXCEEDED_ERROR_MESSAGE.formatted(
            maxNameFieldLength, variableNameLength));
  }
}
