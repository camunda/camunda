/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static java.util.stream.Collectors.toMap;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.message.MessageCorrelationKeyException;
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
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
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
import io.camunda.zeebe.util.collection.Tuple;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class CatchEventBehavior {

  private final ExpressionProcessor expressionProcessor;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final int partitionsCount;
  private final StateWriter stateWriter;

  private final MutableEventScopeInstanceState eventScopeInstanceState;
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

    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();
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

  public void subscribeToEvents(
      final BpmnElementContext context,
      final ExecutableCatchEventSupplier supplier,
      final SideEffects sideEffects,
      final TypedCommandWriter commandWriter)
      throws MessageCorrelationKeyException {

    final List<ExecutableCatchEvent> events = supplier.getEvents();

    // collect all message names from their respective variables, as this might fail and
    // we might need to raise an incident
    final Either<Failure, Map<DirectBuffer, DirectBuffer>> extractedMessageNames =
        evaluateMessageNames(events, context);
    // collect all message correlation keys from their respective variables, as this might fail and
    // we might need to raise an incident. This works the same for timers
    final Either<Failure, Map<DirectBuffer, DirectBuffer>> extractedCorrelationKeys =
        evaluateMessageCorrelationKeys(events, context);
    final Either<Failure, Map<DirectBuffer, Timer>> evaluatedTimers =
        evaluateTimers(events, context);

    if (extractedMessageNames.isLeft()
        || extractedCorrelationKeys.isLeft()
        || evaluatedTimers.isLeft()) {
      // todo: don't throw here, but return an either
      throw new EvaluationException(evaluatedTimers.getLeft().getMessage());
    }

    // if all subscriptions are valid then open the subscriptions
    for (final ExecutableCatchEvent event : events) {
      if (event.isTimer()) {
        subscribeToTimerEvent(
            context.getElementInstanceKey(),
            context.getProcessInstanceKey(),
            context.getProcessDefinitionKey(),
            event.getId(),
            evaluatedTimers.get().get(event.getId()),
            commandWriter,
            sideEffects);
      } else if (event.isMessage()) {
        subscribeToMessageEvent(
            context,
            event,
            extractedCorrelationKeys.get().get(event.getId()),
            extractedMessageNames.get().get((event.getId())),
            sideEffects);
      }
    }
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

  private void subscribeToMessageEvent(
      final BpmnElementContext context,
      final ExecutableCatchEvent catchEvent,
      final DirectBuffer correlationKey,
      final DirectBuffer messageName,
      final SideEffects sideEffects) {

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
    subscription.setElementId(catchEvent.getId());
    subscription.setInterrupting(catchEvent.isInterrupting());

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
                catchEvent.isInterrupting()));
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

  /**
   * Evaluates all message names for events
   *
   * @param events All catch events to evaluate to message names
   * @param context element context used to determine the evaluation scope
   * @return either the first failure it encounters or a mapping of event-ids to message names
   */
  private Either<Failure, Map<DirectBuffer, DirectBuffer>> evaluateMessageNames(
      final List<ExecutableCatchEvent> events, final BpmnElementContext context) {
    return events.stream()
        .filter(ExecutableCatchEvent::isMessage)
        .map(e -> evaluateMessageName(e, context).map(key -> Tuple.of(e.getId(), key)))
        .collect(Either.collector())
        .mapLeft(failures -> failures.get(0))
        .map(t -> t.stream().collect(toMap(Tuple::getLeft, Tuple::getRight)));
  }

  /**
   * Evaluates all message correlation keys for events
   *
   * @param events All catch events to evaluate to message correlation keys
   * @param context element context used to determine the evaluation scope
   * @return either the first failure it encounters or a mapping of event-ids to correlation keys
   */
  private Either<Failure, Map<DirectBuffer, DirectBuffer>> evaluateMessageCorrelationKeys(
      final List<ExecutableCatchEvent> events, final BpmnElementContext context) {
    return events.stream()
        .filter(ExecutableCatchEvent::isMessage)
        .map(e -> evaluateCorrelationKey(e, context).map(key -> Tuple.of(e.getId(), key)))
        .collect(Either.collector())
        .mapLeft(failures -> failures.get(0))
        .map(t -> t.stream().collect(toMap(Tuple::getLeft, Tuple::getRight)));
  }

  /**
   * Evaluates all timer events
   *
   * @param events All catch events to evaluate to timers, any non-timer catch events are skipped
   * @param context element context used to determine the evaluation scope
   * @return either the first failure it encounters or a mapping of event-ids to timers
   */
  private Either<Failure, Map<DirectBuffer, Timer>> evaluateTimers(
      final List<ExecutableCatchEvent> events, final BpmnElementContext context) {
    return events.stream()
        .filter(ExecutableCatchEvent::isTimer)
        .map(e -> evaluateTimer(e, context).map(timer -> Tuple.of(e.getId(), timer)))
        .collect(Either.collector())
        .mapLeft(failures -> failures.get(0))
        .map(t -> t.stream().collect(toMap(Tuple::getLeft, Tuple::getRight)));
  }

  private Either<Failure, DirectBuffer> evaluateCorrelationKey(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    final var expression = event.getMessage().getCorrelationKeyExpression();
    final long scopeKey =
        event.getElementType() == BpmnElementType.BOUNDARY_EVENT
            ? context.getFlowScopeKey()
            : context.getElementInstanceKey();
    return expressionProcessor
        .evaluateMessageCorrelationKeyExpression(expression, scopeKey)
        .map(BufferUtil::wrapString);
  }

  /** @return either a failure or an evaluated timer */
  private Either<Failure, Timer> evaluateTimer(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    final var scopeKey = context.getElementInstanceKey();
    return event.getTimerFactory().apply(expressionProcessor, scopeKey);
  }

  private Either<Failure, DirectBuffer> evaluateMessageName(
      final ExecutableCatchEvent event, final BpmnElementContext context) {
    final var scopeKey = context.getElementInstanceKey();
    final ExecutableMessage message = event.getMessage();
    final Expression messageNameExpression = message.getMessageNameExpression();
    return expressionProcessor
        .evaluateStringExpression(messageNameExpression, scopeKey)
        .map(BufferUtil::wrapString);
  }
}
