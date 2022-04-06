/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.MessageExpressionEvaluation.EvalResult;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.apache.commons.lang3.function.TriFunction;

public class MessageExpressionEvaluation
    implements TriFunction<
        ExpressionProcessor,
        ExecutableCatchEvent,
        BpmnElementContext,
        Either<Failure, EvalResult>> {

  @Override
  public Either<Failure, EvalResult> apply(
      final ExpressionProcessor expressionProcessor,
      final ExecutableCatchEvent catchEvent,
      final BpmnElementContext context) {
    return Either.<Failure, EvalResult>right(new EvalResult(catchEvent))
        .flatMap(result -> evaluateMessageName(expressionProcessor, context, result))
        .flatMap(result -> evaluateCorrelationKey(expressionProcessor, context, result))
        .flatMap(result -> evaluateTimer(expressionProcessor, context, result));
  }

  private Either<Failure, EvalResult> evaluateMessageName(
      final ExpressionProcessor expressionProcessor,
      final BpmnElementContext context,
      final EvalResult evaluation) {
    final var event = evaluation.event();

    if (!event.isMessage()) {
      return Either.right(evaluation);
    }
    final var scopeKey = context.getElementInstanceKey();
    final ExecutableMessage message = event.getMessage();
    final Expression messageNameExpression = message.getMessageNameExpression();
    return expressionProcessor
        .evaluateStringExpression(messageNameExpression, scopeKey)
        .map(BufferUtil::wrapString)
        .map(evaluation::withMessageName);
  }

  private Either<Failure, EvalResult> evaluateCorrelationKey(
      final ExpressionProcessor expressionProcessor,
      final BpmnElementContext context,
      final EvalResult evaluation) {
    final var event = evaluation.event();
    if (!event.isMessage()) {
      return Either.right(evaluation);
    }
    final var expression = event.getMessage().getCorrelationKeyExpression();
    final long scopeKey =
        event.getElementType() == BpmnElementType.BOUNDARY_EVENT
            ? context.getFlowScopeKey()
            : context.getElementInstanceKey();
    return expressionProcessor
        .evaluateMessageCorrelationKeyExpression(expression, scopeKey)
        .map(BufferUtil::wrapString)
        .map(evaluation::withCorrelationKey)
        .mapLeft(f -> new Failure(f.getMessage(), f.getErrorType(), scopeKey));
  }

  private Either<Failure, EvalResult> evaluateTimer(
      final ExpressionProcessor expressionProcessor,
      final BpmnElementContext context,
      final EvalResult evaluation) {
    final var event = evaluation.event();
    if (!event.isTimer()) {
      return Either.right(evaluation);
    }
    final var scopeKey = context.getElementInstanceKey();
    return event.getTimerFactory().apply(expressionProcessor, scopeKey).map(evaluation::withTimer);
  }

  public static final class EvalResult {

    private final ExecutableCatchEvent event;
    private DirectBuffer messageName;
    private DirectBuffer correlationKey;
    private Timer timer;

    public EvalResult(final ExecutableCatchEvent event) {
      this.event = event;
    }

    public EvalResult withMessageName(final DirectBuffer messageName) {
      this.messageName = messageName;
      return this;
    }

    public EvalResult withCorrelationKey(final DirectBuffer correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public EvalResult withTimer(final Timer timer) {
      this.timer = timer;
      return this;
    }

    public boolean isMessage() {
      return event.isMessage();
    }

    public boolean isTimer() {
      return event.isTimer();
    }

    public ExecutableCatchEvent event() {
      return event;
    }

    public DirectBuffer messageName() {
      return messageName;
    }

    public DirectBuffer correlationKey() {
      return correlationKey;
    }

    public Timer timer() {
      return timer;
    }
  }
}
