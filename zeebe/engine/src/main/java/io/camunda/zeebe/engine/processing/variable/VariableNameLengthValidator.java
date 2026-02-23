/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackType;
import io.camunda.zeebe.msgpack.spec.MsgpackReaderException;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;

public final class VariableNameLengthValidator {

  private static final int MAX_NAME_FIELD_LENGTH =
      EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH;

  private static final String VARIABLE_NAME_LENGTH_EXCEEDED_ERROR_MESSAGE =
      "Expected variable names to be no longer than %d characters, but found one with length %d.";

  private VariableNameLengthValidator() {}

  public static Either<Rejection, Object> validateVariableNameLength(
      final DirectBuffer variablesBuffer) {
    if (variablesBuffer == null || variablesBuffer.capacity() == 0) {
      return Either.right(null);
    }

    final var reader = new MsgPackReader().wrap(variablesBuffer, 0, variablesBuffer.capacity());

    try {
      final var rootToken = reader.readToken();
      if (rootToken.getType() != MsgPackType.MAP) {
        return Either.right(null);
      }

      for (int i = 0; i < rootToken.getSize(); i++) {
        final var nameToken = reader.readToken();
        if (nameToken.getType() != MsgPackType.STRING) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  "Expected variable names to be strings in a msgpack object."));
        }

        final String variableName = bufferAsString(nameToken.getValueBuffer());
        if (variableName.length() > MAX_NAME_FIELD_LENGTH) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  VARIABLE_NAME_LENGTH_EXCEEDED_ERROR_MESSAGE.formatted(
                      MAX_NAME_FIELD_LENGTH, variableName.length())));
        }

        reader.skipValue();
      }

      return Either.right(null);
    } catch (final MsgpackReaderException exception) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected variables to be valid msgpack, but it could not be read: '%s'"
                  .formatted(exception.getMessage())));
    }
  }
}
