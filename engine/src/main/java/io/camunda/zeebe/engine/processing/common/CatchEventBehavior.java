/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffects;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.util.List;
import org.agrona.DirectBuffer;

public final class CatchEventBehavior {

  private final ExpressionProcessor expressionProcessor;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final int partitionsCount;
  private final StateWriter stateWriter;

  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final TimerInstanceState timerInstanceState;

  private final ProcessMessageSubscriptionRecord subscription =
      new ProcessMessageSubscriptionRecord();
  private final TimerRecord timerRecord = new TimerRecord();
  private final DueDateTimerChecker timerChecker;
  private final KeyGenerator keyGenerator;

  public CatchEventBehavior(
      final MutableZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final SubscriptionCommandSender subscriptionCommandSender,
      final StateWriter stateWriter,
      final DueDateTimerChecker timerChecker,
      final int partitionsCount) {
    this.expressionProcessor = expressionProcessor;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.stateWriter = stateWriter;
    this.partitionsCount = partitionsCount;

    timerInstanceState = zeebeState.getTimerState();
    processMessageSubscriptionState = zeebeState.getProcessMessageSubscriptionState();

    keyGenerator = zeebeState.getKeyGenerator();

    this.timerChecker = timerChecker;
  }

  public void unsubscribeFromEvents(
      final BpmnElementContext context,
      final TypedCommandWriter commandWriter,
      final SideEffects sideEffects) {

    unsubscribeFromTimerEvents(context, commandWriter);
    unsubscribeFromMessageEvents(context, sideEffects);
  }

  /** @return either a failure or nothing */
  public Either<Failure, Void> subscribeToEvents(
      final BpmnElementContext context,
      final ExecutableCatchEventSupplier supplier,
      final SideEffects sideEffects,
      final TypedCommandWriter commandWriter) {
    final var evaluationResults =
        supplier.getEvents().stream()
            .filter(event -> event.isTimer() || event.isMessage())
            .map(event -> evalExpressions(event, context))
            .collect(Either.collectorFoldingLeft());

    evaluationResults.ifRight(
        results -> {
          subscribeToMessageEvents(context, sideEffects, results);
          subscribeToTimerEvents(context, sideEffects, commandWriter, results);
        });

    return evaluationResults.map(r -> null);
  }

  private Either<Failure, EvalResult> evalExpressions(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    return Either.<Failure, EvalResult>right(new EvalResult(event))
        .flatMap(result -> evaluateMessageName(event, context).map(result::messageName))
        .flatMap(result -> evaluateCorrelationKey(event, context).map(result::correlationKey))
        .flatMap(result -> evaluateTimer(event, context).map(result::timer));
  }

  private Either<Failure, DirectBuffer> evaluateMessageName(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    if (!event.isMessage()) {
      return Either.right(null);
    }
    final var scopeKey = context.getElementInstanceKey();
    final ExecutableMessage message = event.getMessage();
    final Expression messageNameExpression = message.getMessageNameExpression();
    return expressionProcessor
        .evaluateStringExpression(messageNameExpression, scopeKey)
        .map(BufferUtil::wrapString);
  }

  private Either<Failure, DirectBuffer> evaluateCorrelationKey(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    if (!event.isMessage()) {
      return Either.right(null);
    }
    final var expression = event.getMessage().getCorrelationKeyExpression();
    final long scopeKey =
        event.getElementType() == BpmnElementType.BOUNDARY_EVENT
            ? context.getFlowScopeKey()
            : context.getElementInstanceKey();
    return expressionProcessor
        .evaluateMessageCorrelationKeyExpression(expression, scopeKey)
        .map(BufferUtil::wrapString)
        .mapLeft(f -> new Failure(f.getMessage(), f.getErrorType(), scopeKey));
  }

  private Either<Failure, Timer> evaluateTimer(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    if (!event.isTimer()) {
      return Either.right(null);
    }
    final var scopeKey = context.getElementInstanceKey();
    return event.getTimerFactory().apply(expressionProcessor, scopeKey);
  }

  private void subscribeToMessageEvents(
      final BpmnElementContext context,
      final SideEffects sideEffects,
      final List<EvalResult> results) {
    results.stream()
        .filter(EvalResult::isMessage)
        .forEach(result -> subscribeToMessageEvent(context, sideEffects, result));
  }

  private void subscribeToMessageEvent(
      final BpmnElementContext context, final SideEffects sideEffects, final EvalResult result) {
    final var event = result.event;
    final var correlationKey = result.correlationKey;
    final var messageName = result.messageName;

    final long processInstanceKey = context.getProcessInstanceKey();
    final DirectBuffer bpmnProcessId = cloneBuffer(context.getBpmnProcessId());
    final long elementInstanceKey = context.getElementInstanceKey();

    final int subscriptionPartitionId =
        SubscriptionUtil.getSubscriptionPartitionId(correlationKey, partitionsCount);

    subscription.setSubscriptionPartitionId(subscriptionPartitionId);
    subscription.setMessageName(messageName);
    subscription.setElementInstanceKey(elementInstanceKey);
    subscription.setProcessInstanceKey(processInstanceKey);
    subscription.setBpmnProcessId(bpmnProcessId);
    subscription.setCorrelationKey(correlationKey);
    subscription.setElementId(event.getId());
    subscription.setInterrupting(event.isInterrupting());

    final var subscriptionKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        subscriptionKey, ProcessMessageSubscriptionIntent.CREATING, subscription);

    sideEffects.add(
        () ->
            sendOpenMessageSubscription(
                subscriptionPartitionId,
                processInstanceKey,
                elementInstanceKey,
                bpmnProcessId,
                messageName,
                correlationKey,
                event.isInterrupting()));
  }

  private void subscribeToTimerEvents(
      final BpmnElementContext context,
      final SideEffects sideEffects,
      final TypedCommandWriter commandWriter,
      final List<EvalResult> results) {
    results.stream()
        .filter(EvalResult::isTimer)
        .forEach(
            result -> {
              final var event = result.event;
              final var timer = result.timer;
              subscribeToTimerEvent(
                  context.getElementInstanceKey(),
                  context.getProcessInstanceKey(),
                  context.getProcessDefinitionKey(),
                  event.getId(),
                  timer,
                  commandWriter,
                  sideEffects);
            });
  }

  public void subscribeToTimerEvent(
      final long elementInstanceKey,
      final long processInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer handlerNodeId,
      final Timer timer,
      final TypedCommandWriter commandWriter,
      final SideEffects sideEffects) {
    final long dueDate = timer.getDueDate(ActorClock.currentTimeMillis());
    timerRecord.reset();
    timerRecord
        .setRepetitions(timer.getRepetitions())
        .setDueDate(dueDate)
        .setElementInstanceKey(elementInstanceKey)
        .setProcessInstanceKey(processInstanceKey)
        .setTargetElementId(handlerNodeId)
        .setProcessDefinitionKey(processDefinitionKey);

    sideEffects.add(
        () -> {
          /* timerChecker implements onRecovered to recover from restart, so no need to schedule
          this in TimerCreatedApplier.*/
          timerChecker.scheduleTimer(dueDate);
          return true;
        });

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), TimerIntent.CREATED, timerRecord);
  }

  private void unsubscribeFromTimerEvents(
      final BpmnElementContext context, final TypedCommandWriter commandWriter) {
    timerInstanceState.forEachTimerForElementInstance(
        context.getElementInstanceKey(), t -> unsubscribeFromTimerEvent(t, commandWriter));
  }

  public void unsubscribeFromTimerEvent(
      final TimerInstance timer, final TypedCommandWriter commandWriter) {
    timerRecord.reset();
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setProcessInstanceKey(timer.getProcessInstanceKey())
        .setDueDate(timer.getDueDate())
        .setRepetitions(timer.getRepetitions())
        .setTargetElementId(timer.getHandlerNodeId())
        .setProcessDefinitionKey(timer.getProcessDefinitionKey());

    commandWriter.appendFollowUpCommand(timer.getKey(), TimerIntent.CANCEL, timerRecord);
  }

  private void unsubscribeFromMessageEvents(
      final BpmnElementContext context, final SideEffects sideEffects) {
    processMessageSubscriptionState.visitElementSubscriptions(
        context.getElementInstanceKey(),
        subscription -> unsubscribeFromMessageEvent(subscription, sideEffects));
  }

  private boolean unsubscribeFromMessageEvent(
      final ProcessMessageSubscription subscription, final SideEffects sideEffects) {

    final DirectBuffer messageName = cloneBuffer(subscription.getRecord().getMessageNameBuffer());
    final int subscriptionPartitionId = subscription.getRecord().getSubscriptionPartitionId();
    final long processInstanceKey = subscription.getRecord().getProcessInstanceKey();
    final long elementInstanceKey = subscription.getRecord().getElementInstanceKey();

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.DELETING, subscription.getRecord());
    sideEffects.add(
        () ->
            sendCloseMessageSubscriptionCommand(
                subscriptionPartitionId, processInstanceKey, elementInstanceKey, messageName));

    return true;
  }

  private boolean sendCloseMessageSubscriptionCommand(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {
    return subscriptionCommandSender.closeMessageSubscription(
        subscriptionPartitionId, processInstanceKey, elementInstanceKey, messageName);
  }

  private boolean sendOpenMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate) {
    return subscriptionCommandSender.openMessageSubscription(
        subscriptionPartitionId,
        processInstanceKey,
        elementInstanceKey,
        bpmnProcessId,
        messageName,
        correlationKey,
        closeOnCorrelate);
  }

  private static class EvalResult {
    private final ExecutableCatchEvent event;
    private DirectBuffer messageName;
    private DirectBuffer correlationKey;
    private Timer timer;

    public EvalResult(final ExecutableCatchEvent event) {
      this.event = event;
    }

    public EvalResult messageName(final DirectBuffer messageName) {
      this.messageName = messageName;
      return this;
    }

    public EvalResult correlationKey(final DirectBuffer correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public EvalResult timer(final Timer timer) {
      this.timer = timer;
      return this;
    }

    public ExecutableCatchEvent event() {
      return event;
    }

    public boolean isMessage() {
      return event.isMessage();
    }

    public boolean isTimer() {
      return event.isTimer();
    }
  }
}
