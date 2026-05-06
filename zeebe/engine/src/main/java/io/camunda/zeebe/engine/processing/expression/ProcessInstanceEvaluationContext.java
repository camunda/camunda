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
 * <p>Provides {@code key} (the process instance key) and {@code businessId} (the business id, or
 * {@code null} when none was set on creation).
 */
public final class ProcessInstanceEvaluationContext implements ScopedEvaluationContext {

  private final ElementInstanceState elementInstanceState;
  private final long processInstanceKey;
  private final String businessId;

  public ProcessInstanceEvaluationContext(final ElementInstanceState elementInstanceState) {
    this(elementInstanceState, -1, null);
  }

  private ProcessInstanceEvaluationContext(
      final ElementInstanceState elementInstanceState,
      final long processInstanceKey,
      final String businessId) {
    this.elementInstanceState = elementInstanceState;
    this.processInstanceKey = processInstanceKey;
    this.businessId = businessId;
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    final var elementInstance = elementInstanceState.getInstance(scopeKey);
    if (elementInstance == null) {
      return this;
    }
    final var record = elementInstance.getValue();
    final var rawBusinessId = record.getBusinessId();
    return new ProcessInstanceEvaluationContext(
        elementInstanceState,
        record.getProcessInstanceKey(),
        rawBusinessId == null || rawBusinessId.isEmpty() ? null : rawBusinessId);
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (processInstanceKey < 0) {
      return Either.left(null);
    }
    return switch (variableName) {
      case "key" ->
          Either.left(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(processInstanceKey)));
      case "businessId" ->
          businessId == null
              ? Either.left(null)
              : Either.left(
                  BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack((Object) businessId)));
      default -> Either.left(null);
    };
  }
}
