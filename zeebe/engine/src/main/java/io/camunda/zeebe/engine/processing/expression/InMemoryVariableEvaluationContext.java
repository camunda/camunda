/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class InMemoryVariableEvaluationContext implements ScopedEvaluationContext {
  private final Map<String, Object> variables;

  public InMemoryVariableEvaluationContext(final Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (variables.containsKey(variableName)) {
      final var value = variables.get(variableName);
      final var msgPackBytes = MsgPackConverter.convertToMsgPack(value);
      return Either.left(BufferUtil.wrapArray(msgPackBytes));
    }
    return Either.left(null);
  }
}
