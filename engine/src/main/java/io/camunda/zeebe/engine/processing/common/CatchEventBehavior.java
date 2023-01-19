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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffects;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

public final class CatchEventBehavior {

  private final ExpressionProcessor expressionProcessor;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final int partitionsCount;
  private final StateWriter stateWriter;

  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final TimerInstanceState timerInstanceState;
  private final ProcessState processState;

  private final ProcessMessageSubscriptionRecord subscription =
      new ProcessMessageSubscriptionRecord();
  private final TimerRecord timerRecord = new TimerRecord();
  private final DueDateTimerChecker timerChecker;
  private final KeyGenerator keyGenerator;
  private final int currentPartitionId;

  public CatchEventBehavior(
      final ZeebeState zeebeState,
      final KeyGenerator keyGenerator,
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
    processState = zeebeState.getProcessState();

    this.keyGenerator = keyGenerator;
    this.timerChecker = timerChecker;

    currentPartitionId = zeebeState.getPartitionId();
  }

  /**
   * Unsubscribe from all events in the scope of the element instance.
   *
   * @param elementInstanceKey the element instance key to subscript from
   * @param sideEffects the side effects for unsubscribe actions
   */
  public void unsubscribeFromEvents(final long elementInstanceKey, final SideEffects sideEffects) {
    unsubscribeFromEvents(elementInstanceKey, sideEffects, elementId -> true);
  }

  /**
   * Unsubscribe from all event subprocesses in the scope of the element instance. Ignores other
   * event subscriptions in the scope.
   *
   * @param context the context to subscript from
   * @param sideEffects the side effects for unsubscribe actions
   */
  public void unsubscribeEventSubprocesses(
      final BpmnElementContext context, final SideEffects sideEffects) {
    unsubscribeFromEvents(
        context.getElementInstanceKey(),
        sideEffects,
        elementId -> isEventSubprocess(context, elementId));
  }

  private boolean isEventSubprocess(
      final BpmnElementContext context, final DirectBuffer elementId) {

    final var element =
        processState.getFlowElement(
            context.getProcessDefinitionKey(), elementId, ExecutableFlowElement.class);

    return element.getElementType() == BpmnElementType.START_EVENT
        && element.getFlowScope().getElementType() == BpmnElementType.EVENT_SUB_PROCESS;
  }

  /**
   * Unsubscribe from all events in the scope of the element instance that matches the given filter.
   * Ignore other event subscriptions that don't match the filter.
   *
   * @param elementInstanceKey the element instance key to subscript from
   * @param sideEffects the side effects for unsubscribe actions
   * @param elementIdFilter the filter for events to unsubscribe
   */
  private void unsubscribeFromEvents(
      final long elementInstanceKey,
      final SideEffects sideEffects,
      final Predicate<DirectBuffer> elementIdFilter) {

    unsubscribeFromTimerEvents(elementInstanceKey, elementIdFilter);
    unsubscribeFromMessageEvents(elementInstanceKey, sideEffects, elementIdFilter);
  }

  /**
   * @return either a failure or nothing
   */
  public Either<Failure, Void> subscribeToEvents(
      final BpmnElementContext context,
      final ExecutableCatchEventSupplier supplier,
      final SideEffects sideEffects) {
    final var evaluationResults =
        supplier.getEvents().stream()
            .filter(event -> event.isTimer() || event.isMessage())
            .map(event -> evalExpressions(expressionProcessor, event, context))
            .collect(Either.collectorFoldingLeft());

    evaluationResults.ifRight(
        results -> {
          subscribeToMessageEvents(context, sideEffects, results);
          subscribeToTimerEvents(context, sideEffects, results);
        });

    return evaluationResults.map(r -> null);
  }

  private Either<Failure, EvalResult> evalExpressions(
      final ExpressionProcessor ep,
      final ExecutableCatchEvent event,
      final BpmnElementContext context) {
    return Either.<Failure, OngoingEvaluation>right(new OngoingEvaluation(ep, event, context))
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
      return Either.right(evaluation);
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
      return Either.right(evaluation);
    }
    final var scopeKey = context.getElementInstanceKey();
    return event
        .getTimerFactory()
        .apply(evaluation.expressionProcessor(), scopeKey)
        .map(evaluation::recordTimer);
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


              sendOpenMessageSubscription(
                  subscriptionPartitionId,
                  processInstanceKey,
                  elementInstanceKey,
                  bpmnProcessId,
                  messageName,
                  correlationKey,
                  event.isInterrupting());
  }

  private void subscribeToTimerEvents(
      final BpmnElementContext context,
      final SideEffects sideEffects,
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
                  sideEffects);
            });
  }

  public void subscribeToTimerEvent(
      final long elementInstanceKey,
      final long processInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer handlerNodeId,
      final Timer timer,
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
      final long elementInstanceKey, final Predicate<DirectBuffer> elementIdFilter) {
    timerInstanceState.forEachTimerForElementInstance(
        elementInstanceKey,
        timer -> {
          if (elementIdFilter.test(timer.getHandlerNodeId())) {
            unsubscribeFromTimerEvent(timer);
          }
        });
  }

  public void unsubscribeFromTimerEvent(final TimerInstance timer) {
    timerRecord.reset();
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setProcessInstanceKey(timer.getProcessInstanceKey())
        .setDueDate(timer.getDueDate())
        .setRepetitions(timer.getRepetitions())
        .setTargetElementId(timer.getHandlerNodeId())
        .setProcessDefinitionKey(timer.getProcessDefinitionKey());

    stateWriter.appendFollowUpEvent(timer.getKey(), TimerIntent.CANCELED, timerRecord);
  }

  private void unsubscribeFromMessageEvents(
      final long elementInstanceKey,
      final SideEffects sideEffects,
      final Predicate<DirectBuffer> elementIdFilter) {
    processMessageSubscriptionState.visitElementSubscriptions(
        elementInstanceKey,
        subscription -> {
          final var elementId = subscription.getRecord().getElementIdBuffer();
          if (elementIdFilter.test(elementId)) {
            unsubscribeFromMessageEvent(subscription, sideEffects);
          }
          return true;
        });
  }

  private void unsubscribeFromMessageEvent(
      final ProcessMessageSubscription subscription, final SideEffects sideEffects) {

    final DirectBuffer messageName = cloneBuffer(subscription.getRecord().getMessageNameBuffer());
    final int subscriptionPartitionId = subscription.getRecord().getSubscriptionPartitionId();
    final long processInstanceKey = subscription.getRecord().getProcessInstanceKey();
    final long elementInstanceKey = subscription.getRecord().getElementInstanceKey();

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.DELETING, subscription.getRecord());
    sendCloseMessageSubscriptionCommand(
                subscriptionPartitionId, processInstanceKey, elementInstanceKey, messageName);
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

  /**
   * Transient helper object that captures the information necessary to evaluate important
   * expressions for a message, and to capture intermediate results of the evaluation
   */
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

  private record EvalResult(
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
}
