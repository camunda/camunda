/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Evaluation context exposing properties of the current process instance via the {@code
 * camunda.processInstance} namespace.
 *
 * <p>Currently provides {@code key}, the process instance key.
 */
public final class ProcessInstanceEvaluationContext implements ScopedEvaluationContext {

  private final ElementInstanceState elementInstanceState;
  private final long processInstanceKey;

  public ProcessInstanceEvaluationContext(final ElementInstanceState elementInstanceState) {
    this(elementInstanceState, -1);
  }

  private ProcessInstanceEvaluationContext(
      final ElementInstanceState elementInstanceState, final long processInstanceKey) {
    this.elementInstanceState = elementInstanceState;
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    final var elementInstance = elementInstanceState.getInstance(scopeKey);
    if (elementInstance == null) {
      return this;
    }
    return new ProcessInstanceEvaluationContext(
        elementInstanceState, elementInstance.getValue().getProcessInstanceKey());
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (processInstanceKey < 0) {
      return Either.left(null);
    }
    if ("key".equals(variableName)) {
      return Either.left(
          BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(processInstanceKey)));
    }
    return Either.left(null);
  }
}
