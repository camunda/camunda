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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

/**
 * Rejects variables metadata that describes a variable the request does not set, as such metadata
 * would be silently ignored by the engine.
 */
public final class VariablesMetadataValidator {

  private static final String UNKNOWN_VARIABLES_ERROR_MESSAGE =
      "Expected variables metadata to only describe variables set in the request, but found no variable for: %s";
  private static final String INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE =
      "Expected variables to be valid msgpack, but it could not be read: '%s'";

  private VariablesMetadataValidator() {}

  public static void validateVariablesMetadata(
      final DirectBuffer variablesBuffer, final Map<String, ?> variablesMetadata) {
    if (variablesMetadata == null || variablesMetadata.isEmpty()) {
      return;
    }

    final var variableNames = variableNames(variablesBuffer);
    final var unknownNames =
        variablesMetadata.keySet().stream()
            .filter(name -> !variableNames.contains(name))
            .sorted()
            .map("'%s'"::formatted)
            .collect(Collectors.joining(", "));

    if (!unknownNames.isEmpty()) {
      throw new InvalidVariableRequestException(
          UNKNOWN_VARIABLES_ERROR_MESSAGE.formatted(unknownNames));
    }
  }

  private static Set<String> variableNames(final DirectBuffer variablesBuffer) {
    if (variablesBuffer == null || variablesBuffer.capacity() == 0) {
      return Set.of();
    }

    try {
      final var variables = MsgPackConverter.convertToMap(variablesBuffer);
      return variables == null ? Set.of() : variables.keySet();
    } catch (final RuntimeException exception) {
      throw new InvalidVariableRequestException(
          INVALID_VARIABLES_MSGPACK_ERROR_MESSAGE.formatted(exception.getMessage()), exception);
    }
  }
}
