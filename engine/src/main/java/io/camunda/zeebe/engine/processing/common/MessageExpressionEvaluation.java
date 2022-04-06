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
      final ExecutableCatchEvent executableCatchEvent,
      final BpmnElementContext bpmnElementContext) {
    return Either.<Failure, OngoingEvaluation>right(
            new OngoingEvaluation(expressionProcessor, executableCatchEvent, bpmnElementContext))
        .flatMap(this::evaluateMessageName)
        .flatMap(this::evaluateCorrelationKey)
        .flatMap(this::evaluateTimer)
        .map(OngoingEvaluation::getResult);
  }

  private Either<Failure, OngoingEvaluation> evaluateMessageName(
      final OngoingEvaluation evaluation) {
    final var event = evaluation.event();

    if (!event.isMessage()) {
      return Either.right(evaluation);
    }
    final var scopeKey = evaluation.context().getElementInstanceKey();
    final ExecutableMessage message = event.getMessage();
    final Expression messageNameExpression = message.getMessageNameExpression();
    return evaluation
        .expressionProcessor()
        .evaluateStringExpression(messageNameExpression, scopeKey)
        .map(BufferUtil::wrapString)
        .map(evaluation::recordMessageName);
  }

  private Either<Failure, OngoingEvaluation> evaluateCorrelationKey(
      final OngoingEvaluation evaluation) {
    final var event = evaluation.event();
    final var context = evaluation.context();
    if (!event.isMessage()) {
      return Either.right(null);
    }
    final var expression = event.getMessage().getCorrelationKeyExpression();
    final long scopeKey =
        event.getElementType() == BpmnElementType.BOUNDARY_EVENT
            ? context.getFlowScopeKey()
            : context.getElementInstanceKey();
    return evaluation
        .expressionProcessor()
        .evaluateMessageCorrelationKeyExpression(expression, scopeKey)
        .map(BufferUtil::wrapString)
        .map(evaluation::recordCorrelationKey)
        .mapLeft(f -> new Failure(f.getMessage(), f.getErrorType(), scopeKey));
  }

  private Either<Failure, OngoingEvaluation> evaluateTimer(final OngoingEvaluation evaluation) {
    final var event = evaluation.event();
    final var context = evaluation.context();
    if (!event.isTimer()) {
      return Either.right(null);
    }
    final var scopeKey = context.getElementInstanceKey();
    return event
        .getTimerFactory()
        .apply(evaluation.expressionProcessor(), scopeKey)
        .map(evaluation::recordTimer);
  }

  public record EvalResult(
      ExecutableCatchEvent event,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      Timer timer) {

    public boolean isMessage() {
      return event.isMessage();
    }

    public boolean isTimer() {
      return event.isTimer();
    }
  }

  private static class OngoingEvaluation {
    private final ExpressionProcessor expressionProcessor;
    private final ExecutableCatchEvent event;
    private final BpmnElementContext context;
    private DirectBuffer messageName;
    private DirectBuffer correlationKey;
    private Timer timer;

    public OngoingEvaluation(
        final ExpressionProcessor expressionProcessor,
        final ExecutableCatchEvent event,
        final BpmnElementContext context) {
      this.expressionProcessor = expressionProcessor;
      this.event = event;
      this.context = context;
    }

    private ExpressionProcessor expressionProcessor() {
      return expressionProcessor;
    }

    private ExecutableCatchEvent event() {
      return event;
    }

    private BpmnElementContext context() {
      return context;
    }

    public OngoingEvaluation recordMessageName(final DirectBuffer messageName) {
      this.messageName = messageName;
      return this;
    }

    public OngoingEvaluation recordCorrelationKey(final DirectBuffer correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public OngoingEvaluation recordTimer(final Timer timer) {
      this.timer = timer;
      return this;
    }

    EvalResult getResult() {
      return new EvalResult(event, messageName, correlationKey, timer);
    }
  }
}
