/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation;

import io.camunda.zeebe.gateway.cmd.InvalidVariableRequestException;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.DirectBuffer;

public final class VariableNameLengthValidator {

  public static final int DEFAULT_MAX_NAME_FIELD_LENGTH = 32 * 1024;
  private static final String VARIABLE_NAME_LENGTH_EXCEEDED_ERROR_MESSAGE =
      "Expected variable names to be no longer than %d characters, but found one with length %d.";
  private static final String INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE =
      "Expected variables to be valid msgpack, but it could not be read: '%s'";

  private VariableNameLengthValidator() {}

  public static void validateVariableNameLength(
      final DirectBuffer variablesBuffer, final int maxNameFieldLength) {
    if (isEmpty(variablesBuffer)) {
      return;
    }

    final java.util.Map<String, Object> variables;
    try {
      variables = MsgPackConverter.convertToMap(variablesBuffer);
    } catch (final RuntimeException exception) {
      throw new InvalidVariableRequestException(
          INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE.formatted(exception.getMessage()), exception);
    }

    if (variables == null || variables.isEmpty()) {
      return;
    }

    for (final String variableName : variables.keySet()) {
      if (variableName != null && variableName.length() > maxNameFieldLength) {
        throw new InvalidVariableRequestException(
            VARIABLE_NAME_LENGTH_EXCEEDED_ERROR_MESSAGE.formatted(
                maxNameFieldLength, variableName.length()));
      }
    }
  }

  private static boolean isEmpty(final DirectBuffer variablesBuffer) {
    return variablesBuffer == null || variablesBuffer.capacity() == 0;
  }
}
