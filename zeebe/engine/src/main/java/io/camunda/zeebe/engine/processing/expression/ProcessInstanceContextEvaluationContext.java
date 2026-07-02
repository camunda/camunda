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
import org.jspecify.annotations.NullMarked;

/**
 * An {@link EvaluationContext} that provides the {@code camunda.processInstance} namespace,
 * exposing {@code key} as the process instance key for the current execution scope.
 *
 * <p>When the context is not yet scoped (no scope key bound, i.e. key == -1), all lookups return
 * {@code null} — this is the correct behavior for expression sites that run before a process
 * instance exists (timer start events, conditional start events, deployment-time validation).
 *
 * <p>Scoping via {@link #processScoped(long)} resolves the element instance to its process instance
 * key. If the element instance cannot be found, the context falls back to the unscoped (null) state
 * rather than throwing.
 */
@NullMarked
public final class ProcessInstanceContextEvaluationContext implements ScopedEvaluationContext {

  private final ElementInstanceState elementInstanceState;
  private final long processInstanceKey;

  public ProcessInstanceContextEvaluationContext(final ElementInstanceState elementInstanceState) {
    this(elementInstanceState, -1L);
  }

  private ProcessInstanceContextEvaluationContext(
      final ElementInstanceState elementInstanceState, final long processInstanceKey) {
    this.elementInstanceState = elementInstanceState;
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (!"key".equals(variableName) || processInstanceKey == -1L) {
      return ScopedEvaluationContext.NONE_INSTANCE.getVariable(variableName);
    }
    final var msgPackBytes = MsgPackConverter.convertToMsgPack(processInstanceKey);
    return Either.left(BufferUtil.wrapArray(msgPackBytes));
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    final var instance = elementInstanceState.getInstance(scopeKey);
    if (instance == null) {
      return new ProcessInstanceContextEvaluationContext(elementInstanceState, -1L);
    }
    return new ProcessInstanceContextEvaluationContext(
        elementInstanceState, instance.getValue().getProcessInstanceKey());
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    // The process instance key is not tenant-dependent; return this context unchanged.
    return this;
  }
}
