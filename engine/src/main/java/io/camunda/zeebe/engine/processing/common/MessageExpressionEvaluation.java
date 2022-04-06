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
        Either<Failure, ? extends EvalResult>> {

  @Override
  public Either<Failure, ? extends EvalResult> apply(
      final ExpressionProcessor expressionProcessor,
      final ExecutableCatchEvent catchEvent,
      final BpmnElementContext context) {

    if (catchEvent.isMessage()) {
      return Either.<Failure, MessageEvalResult>right(new MessageEvalResult(catchEvent))
          .flatMap(result -> evaluateMessageName(expressionProcessor, context, result))
          .flatMap(result -> evaluateCorrelationKey(expressionProcessor, context, result));
    } else if (catchEvent.isTimer()) {
      return evaluateTimer(expressionProcessor, context, catchEvent);
    } else {
      return Either.left(
          new Failure("CatchEvent %s is neither message nor timer event".formatted(catchEvent)));
    }
  }

  private Either<Failure, MessageEvalResult> evaluateMessageName(
      final ExpressionProcessor expressionProcessor,
      final BpmnElementContext context,
      final MessageEvalResult evaluation) {
    final var event = evaluation.event();
    final var scopeKey = context.getElementInstanceKey();
    final ExecutableMessage message = event.getMessage();
    final Expression messageNameExpression = message.getMessageNameExpression();
    return expressionProcessor
        .evaluateStringExpression(messageNameExpression, scopeKey)
        .map(BufferUtil::wrapString)
        .map(evaluation::withMessageName);
  }

  private Either<Failure, MessageEvalResult> evaluateCorrelationKey(
      final ExpressionProcessor expressionProcessor,
      final BpmnElementContext context,
      final MessageEvalResult evaluation) {
    final var event = evaluation.event();
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

  private Either<Failure, TimerEvalResult> evaluateTimer(
      final ExpressionProcessor expressionProcessor,
      final BpmnElementContext context,
      final ExecutableCatchEvent catchEvent) {
    final var scopeKey = context.getElementInstanceKey();
    return catchEvent
        .getTimerFactory()
        .apply(expressionProcessor, scopeKey)
        .map(timer -> new TimerEvalResult(catchEvent, timer));
  }

  public abstract static class EvalResult {

    private final ExecutableCatchEvent event;

    private EvalResult(final ExecutableCatchEvent event) {
      this.event = event;
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
  }

  public static final class TimerEvalResult extends EvalResult {

    private final Timer timer;

    private TimerEvalResult(final ExecutableCatchEvent event, final Timer timer) {
      super(event);
      this.timer = timer;
    }

    public Timer timer() {
      return timer;
    }
  }

  public static final class MessageEvalResult extends EvalResult {

    private DirectBuffer messageName;
    private DirectBuffer correlationKey;

    private MessageEvalResult(final ExecutableCatchEvent event) {
      super(event);
    }

    public MessageEvalResult withMessageName(final DirectBuffer messageName) {
      this.messageName = messageName;
      return this;
    }

    public MessageEvalResult withCorrelationKey(final DirectBuffer correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public DirectBuffer messageName() {
      return messageName;
    }

    public DirectBuffer correlationKey() {
      return correlationKey;
    }
  }
}
