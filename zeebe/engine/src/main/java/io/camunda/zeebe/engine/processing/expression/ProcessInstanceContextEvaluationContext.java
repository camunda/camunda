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
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link EvaluationContext} that provides the {@code camunda.processInstance} namespace,
 * exposing:
 *
 * <ul>
 *   <li>{@code key} — the process instance key ({@code long}) for the current execution scope.
 *   <li>{@code businessId} — the user-defined business id ({@code String}) for the current process
 *       instance; returns {@code null} when unset. The engine stores an unset business id as the
 *       empty string, so empty is surfaced as {@code null}.
 * </ul>
 *
 * <p>When the context is not yet scoped (no scope key bound, i.e. key == -1), all lookups return
 * {@code null} — this is the correct behavior for expression sites that run before a process
 * instance exists (timer start events, conditional start events, deployment-time validation).
 *
 * <p>Scoping via {@link #processScoped(long)} resolves the element instance lazily to its process
 * instance key. If the element instance cannot be found, the context falls back to the unscoped
 * (null) state rather than throwing.
 */
@NullMarked
public final class ProcessInstanceContextEvaluationContext implements ScopedEvaluationContext {

  private final ElementInstanceState elementInstanceState;
  private final long scopeKey;

  public ProcessInstanceContextEvaluationContext(final ElementInstanceState elementInstanceState) {
    this(elementInstanceState, -1L);
  }

  private ProcessInstanceContextEvaluationContext(
      final ElementInstanceState elementInstanceState, final long scopeKey) {
    this.elementInstanceState = elementInstanceState;
    this.scopeKey = scopeKey;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (scopeKey == -1L) {
      return ScopedEvaluationContext.NONE_INSTANCE.getVariable(variableName);
    }

    if ("key".equals(variableName)) {
      final var elementInstance = elementInstanceState.getInstance(scopeKey);
      if (elementInstance == null) {
        return ScopedEvaluationContext.NONE_INSTANCE.getVariable(variableName);
      }
      final long processInstanceKey = elementInstance.getValue().getProcessInstanceKey();
      final var msgPackBytes = MsgPackConverter.convertToMsgPack(processInstanceKey);
      return Either.left(BufferUtil.wrapArray(msgPackBytes));
    }

    if ("businessId".equals(variableName)) {
      final var elementInstance = elementInstanceState.getInstance(scopeKey);
      if (elementInstance == null) {
        return ScopedEvaluationContext.NONE_INSTANCE.getVariable(variableName);
      }
      final long processInstanceKey = elementInstance.getValue().getProcessInstanceKey();
      final long elementInstanceKey = elementInstance.getValue().getElementInstanceKey();
      // Business ID does not exist on all element instances, only on the process instance
      final var processInstance =
          processInstanceKey != elementInstanceKey
              ? elementInstanceState.getInstance(processInstanceKey)
              : elementInstance;
      final String businessId =
          processInstance == null ? null : processInstance.getValue().getBusinessId();
      if (businessId == null || businessId.isEmpty()) {
        return ScopedEvaluationContext.NONE_INSTANCE.getVariable(variableName);
      }
      final var writer = new MsgPackWriter();
      final var buffer = new ExpandableArrayBuffer();
      writer.wrap(buffer, 0);
      writer.writeString(BufferUtil.wrapString(businessId));
      return Either.left(new UnsafeBuffer(buffer, 0, writer.getOffset()));
    }

    return ScopedEvaluationContext.NONE_INSTANCE.getVariable(variableName);
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    return new ProcessInstanceContextEvaluationContext(elementInstanceState, scopeKey);
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    // The process instance key is not tenant-dependent; return this context unchanged.
    return this;
  }
}
