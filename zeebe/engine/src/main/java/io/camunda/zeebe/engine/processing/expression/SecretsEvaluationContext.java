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
import org.agrona.DirectBuffer;

/**
 * Returns any requested variable name as a literal reference of the form {@code
 * "camunda.secrets.<name>"}.
 *
 * <p>Registered under the {@code camunda.secrets} namespace so FEEL expressions like {@code
 * camunda.secrets.MY_TOKEN} evaluate to the literal string {@code "camunda.secrets.MY_TOKEN"}
 * instead of a path lookup. Resolution to the actual secret value happens outside the engine; the
 * literal reference flows through variables and is resolved by the gateway on demand.
 */
public final class SecretsEvaluationContext implements ScopedEvaluationContext {

  private static final String PREFIX = "camunda.secrets.";

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    final Object literal = PREFIX + variableName;
    return Either.left(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(literal)));
  }
}
